/*
Copyright 2023 Fausto Spoto

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

    http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
*/

package io.mokamint.plotter.internal;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.channels.Channels;
import java.nio.channels.FileChannel;
import java.nio.charset.Charset;
import java.nio.file.Path;
import java.security.NoSuchAlgorithmException;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import java.util.function.IntConsumer;
import java.util.logging.Logger;
import java.util.stream.LongStream;

import io.hotmoka.annotations.Immutable;
import io.hotmoka.crypto.HashingAlgorithms;
import io.hotmoka.crypto.api.HashingAlgorithm;
import io.hotmoka.exceptions.CheckRunnable;
import io.hotmoka.exceptions.CheckSupplier;
import io.hotmoka.exceptions.UncheckConsumer;
import io.hotmoka.exceptions.UncheckFunction;
import io.hotmoka.marshalling.UnmarshallingContexts;
import io.mokamint.nonce.Deadlines;
import io.mokamint.nonce.Nonces;
import io.mokamint.nonce.Prologs;
import io.mokamint.nonce.api.Deadline;
import io.mokamint.nonce.api.DeadlineDescription;
import io.mokamint.nonce.api.Prolog;
import io.mokamint.plotter.api.Plot;

/**
 * An implementation of a plot file. There are two ways of creating this implementation:
 * by computing a brand new plot file on disk, or by loading a previously created plot file.
 */
@Immutable
public class PlotImpl implements Plot {

	private final static Logger logger = Logger.getLogger(PlotImpl.class.getName());

	/**
	 * The file that backs up this plot.
	 */
	private final RandomAccessFile reader;

	/**
	 * The channel used to access the file of this plot.
	 */
	private final FileChannel channel;

	/**
	 * Generic data that identifies, for instance, the creator of the plot.
	 * This can be really anything by is limited to {@link Deadline#MAX_PROLOG_SIZE} bytes.
	 */
	private final Prolog prolog;

	/**
	 * The starting progressive number of the nonces inside this plot.
	 */
	private final long start;

	/**
	 * The number of nonces in this plot. 
	 */
	private final long length;

	/**
	 * The hashing algorithm used by this plot.
	 */
	private final HashingAlgorithm<byte[]> hashing;

	/**
	 * Loads a plot file already existing on disk.
	 * 
	 * @param path the path to the file that must be loaded
	 * @throws IOException if the file of the plot cannot be read
	 * @throws NoSuchAlgorithmException if the plot file uses an unknown cryptographic algorithm
	 */
	public PlotImpl(Path path) throws IOException, NoSuchAlgorithmException {
		this.reader = new RandomAccessFile(path.toFile(), "r");
		this.channel = reader.getChannel();

		int prologLength = reader.readInt();
		if (prologLength > Prolog.MAX_PROLOG_SIZE)
			throw new IllegalArgumentException("the maximal prolog size is " + Prolog.MAX_PROLOG_SIZE);
		var prologBytes = new byte[prologLength];
		if (reader.read(prologBytes) != prologLength)
			throw new IOException("Cannot read the prolog of the plot file");

		try (var context = UnmarshallingContexts.of(new ByteArrayInputStream(prologBytes))) {
			this.prolog = Prologs.from(context);
		}

		this.start = reader.readLong();
		if (start < 0)
			throw new IllegalArgumentException("the plot starting number cannot be negative");

		this.length = reader.readLong();
		if (length < 1)
			throw new IllegalArgumentException("the plot length must be positive");

		int hashingNameLength = reader.readInt();
		var hashingNameBytes = new byte[hashingNameLength];
		if (reader.read(hashingNameBytes) != hashingNameLength)
			throw new IOException("Cannot read the name of the hashing algorithm used for the plot file");
		var hashingName = new String(hashingNameBytes, Charset.forName("UTF-8"));
		this.hashing = HashingAlgorithms.of(hashingName, Function.identity());
	}

	/**
	 * Creates, on the file system, a plot file containing sequential nonces for the given prolog,
	 * by using the given hashing algorithm.
	 * 
	 * @param path the path to the file where the plot must be dumped
	 * @param prolog generic data that identifies, for instance, the creator
	 *               of the plot. This can be really anything but cannot be {@code null}
	 *               nor longer than {@link Deadline#MAX_PROLOG_SIZE} bytes
	 * @param start the starting progressive number of the nonces to generate in the plot.
	 *              This must be non-negative
	 * @param length the number of nonces to generate. This must be positive
	 * @param hashing the hashing algorithm to use for creating the nonces
	 * @param onNewPercent a handler called with the percent of work already alreadyDone, for feedback
	 * @throws IOException if the plot file cannot be written into {@code path}
	 */
	public PlotImpl(Path path, Prolog prolog, long start, long length, HashingAlgorithm<byte[]> hashing, IntConsumer onNewPercent) throws IOException {
		Objects.requireNonNull(prolog, "prolog cannot be null");
		Objects.requireNonNull(hashing, "hashing cannot be null");
		Objects.requireNonNull(onNewPercent, "onNewPercent cannot be null");

		if (start < 0)
			throw new IllegalArgumentException("start cannot be negative");
		
		if (length < 1)
			throw new IllegalArgumentException("length must be positive");

		this.prolog = prolog;
		this.start = start;
		this.length = length;
		this.hashing = hashing;

		new Dumper(path, onNewPercent);

		this.reader = new RandomAccessFile(path.toFile(), "r");
		this.channel = reader.getChannel();
	}

	/**
	 * Utility class that creates a plot into a file.
	 */
	private class Dumper {
		private final FileChannel channel;
		private final int nonceSize = (Deadline.MAX_SCOOP_NUMBER + 1) * 2 * hashing.length();
		private final int metadataSize = getMetadataSize();
		private final long plotSize = metadataSize + length * nonceSize;
		private final IntConsumer onNewPercent;
		private final AtomicInteger alreadyDone = new AtomicInteger();

		private Dumper(Path where, IntConsumer onNewPercent) throws IOException {
			long startTime = System.currentTimeMillis();
			logger.info("Starting creating a plot file of " + plotSize + " bytes");

			this.onNewPercent = onNewPercent;
		
			try (var writer = new RandomAccessFile(where.toFile(), "rw"); var channel = this.channel = writer.getChannel()) {
				sizePlotFile();
				dumpMetadata();
				dumpNonces();
			}
		
			logger.info("Plot file created in " + (System.currentTimeMillis() - startTime) + "ms");
		}

		/**
		 * Initializes the plot file: it is created at its final size, initially containing random bytes.
		 * 
		 * @throws IOException if the file cannot be initialized
		 */
		private void sizePlotFile() throws IOException {
			// by forcing a write to the last byte of the file, we guarantee
			// that it is fully created (but randomly initialized, for now)
			channel.position(plotSize - 1);
			var buffer = ByteBuffer.wrap(new byte[1]);
			channel.write(buffer);
		}

		private void dumpMetadata() throws IOException {
			var buffer = ByteBuffer.allocate(metadataSize);
			byte[] prologBytes = prolog.toByteArray();
			buffer.putInt(prologBytes.length);
			buffer.put(prologBytes);
			buffer.putLong(start);
			buffer.putLong(length);
			byte[] name = hashing.getName().getBytes(Charset.forName("UTF-8"));
			buffer.putInt(name.length);
			buffer.put(name);

			try (var source = Channels.newChannel(new ByteArrayInputStream(buffer.array()))) {
				channel.transferFrom(source, 0L, metadataSize);
			}
		}

		private void dumpNonces() throws IOException {
			CheckRunnable.check(IOException.class, () ->
				LongStream.range(start, start + length)
					.parallel()
					.mapToObj(Long::valueOf)
					.forEach(UncheckConsumer.uncheck(this::dumpNonce))
			);
		}

		/**
		 * Write into the file the data of the nonce with the given progressive inside the plot file.
		 * 
		 * @param n the number of the nonce to dumpo inside the file. It goes from
		 *          {@link PlotImpl#start} (inclusive)
		 *          to {@link PlotImpl#start} + {@link PlotImpl#length} (exclusive)
		 * @throws IOException if the nonce cannot be written into the file
		 */
		private void dumpNonce(long n) throws IOException {
			// the hashing algorithm is cloned to avoid thread contention
			Nonces.of(prolog, n, hashing.clone())
				.dumpInto(channel, metadataSize, n - start, length);

			int counter = alreadyDone.getAndIncrement();
			long percent = (counter + 1) * 100L / length;
			if (percent > counter * 100L / length)
				onNewPercent.accept((int) percent);
		}
	}

	@Override
	public Prolog getProlog() {
		return prolog;
	}

	@Override
	public long getStart() {
		return start;
	}

	@Override
	public long getLength() {
		return length;
	}

	@Override
	public HashingAlgorithm<byte[]> getHashing() {
		return hashing;
	}

	@Override
	public Deadline getSmallestDeadline(DeadlineDescription description) throws IOException {
		if (!description.getHashing().getName().equals(hashing.getName()))
			throw new IllegalArgumentException("deadline description and plot file use different hashing algorithms");

		return new SmallestDeadlineFinder(description).deadline;
	}

	/**
	 * Yields the size of the metadata reported before the nonces.
	 * 
	 * @return the size of the metadata, in bytes
	 */
	private int getMetadataSize() {
		return 4 + prolog.toByteArray().length // prolog
			+ 8 // start
			+ 8 // length
			+ 4 + hashing.getName().getBytes(Charset.forName("UTF-8")).length; // hashing algorithm
	}

	private class SmallestDeadlineFinder {
		private final int scoopNumber;
		private final byte[] data;
		private final Deadline deadline;
		private final int scoopSize = 2 * hashing.length();
		private final long groupSize = length * scoopSize;
		private final int metadataSize = getMetadataSize();

		private SmallestDeadlineFinder(DeadlineDescription description) throws IOException {
			this.scoopNumber = description.getScoopNumber();
			this.data = description.getData();
			this.deadline = CheckSupplier.check(IOException.class, () ->
				LongStream.range(start, start + length)
					.mapToObj(Long::valueOf)
					.parallel()
					.map(UncheckFunction.uncheck(this::mkDeadline))
					.min(Deadline::compareByValue)
					.get() // OK, since plots contain at least one nonce
			);
		}

		private Deadline mkDeadline(long n) throws IOException {
			return Deadlines.of(prolog, n, hashing.hash(extractScoopAndConcatData(n - start)), scoopNumber, data, hashing);
		}

		/**
		 * Extracts the scoop (two hashes) number {@code scoopNumber} from the nonce
		 * number {@code progressive} of this plot file, and concatenates the {@code data} at its end.
		 * 
		 * @param progressive the progressive number of the nonce whose scoop must be extracted,
		 *                    between 0 (inclusive) and {@code length} (exclusive)
		 * @return the scoop data (two hashes)
		 * @throws IOException if an I/O error occurs
		 */
		private byte[] extractScoopAndConcatData(long progressive) throws IOException {
			try (var os = new ByteArrayOutputStream(); var destination = Channels.newChannel(os)) {
				channel.transferTo(metadataSize + scoopNumber * groupSize + progressive * scoopSize, scoopSize, destination);
				destination.write(ByteBuffer.wrap(data));
				return os.toByteArray();
			}
		}
	}

	@Override
	public void close() throws IOException {
		try {
			reader.close();
		}
		finally {
			channel.close();
		}
	}
}