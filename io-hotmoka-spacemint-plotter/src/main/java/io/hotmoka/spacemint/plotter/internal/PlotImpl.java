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

package io.hotmoka.spacemint.plotter.internal;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.io.UncheckedIOException;
import java.nio.ByteBuffer;
import java.nio.channels.Channels;
import java.nio.channels.FileChannel;
import java.nio.charset.Charset;
import java.nio.file.Path;
import java.security.NoSuchAlgorithmException;
import java.util.logging.Logger;
import java.util.stream.LongStream;

import io.hotmoka.crypto.HashingAlgorithm;
import io.hotmoka.spacemint.plotter.Nonce;
import io.hotmoka.spacemint.plotter.Plot;

/**
 */
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
	 * This can be really anything.
	 */
	private final byte[] prolog;

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
	 * Loads a plot file.
	 * 
	 * @param path the path to the file that contains the plot
	 * @throws IOException if the file of the plot cannot be read
	 * @throws NoSuchAlgorithmException if the plot has been created with
	 *                                  a hashing algorithm that is not available
	 */
	public PlotImpl(Path path) throws IOException, NoSuchAlgorithmException {
		this.reader = new RandomAccessFile(path.toFile(), "r");
		this.channel = reader.getChannel();

		int prologLength = reader.readInt();
		if (prologLength > MAX_PROLOG_SIZE)
			throw new IllegalArgumentException("the maximal prolog size is " + MAX_PROLOG_SIZE);
		this.prolog = new byte[prologLength];
		if (reader.read(prolog) != prologLength)
			throw new IOException("cannot read the prolog of the plot file");

		this.start = reader.readLong();
		if (start < 0)
			throw new IllegalArgumentException("the plot starting number cannot be negative");

		this.length = reader.readLong();
		if (length < 1)
			throw new IllegalArgumentException("the plot length must be positive");

		int hashingNameLength = reader.readInt();
		byte[] hashingNameBytes = new byte[hashingNameLength];
		if (reader.read(hashingNameBytes) != hashingNameLength)
			throw new IOException("cannot read the name of the hashing algorithm used for the plot file");
		String hashingName = new String(hashingNameBytes, Charset.forName("UTF-8"));
		this.hashing = HashingAlgorithm.mk(hashingName, (byte[] bytes) -> bytes);
	}

	/**
	 * Creates on the file system a plot file containing sequential nonces for the given prolog.
	 * 
	 * @param path the path to the file where the plot must be dumped
	 * @param prolog generic data that identifies, for instance, the creator
	 *               of the plot. This can be really anything but cannot be {@code null}
	 * @param start the starting progressive number of the nonces in the plot.
	 *              This must be non-negative
	 * @param length the number of nonces to generate. This must be non-negative
	 * @param hashing the hashing algorithm to use for creating the nonces
	 * @throws IOException if the plot file could not be written into {@code path}
	 */
	public PlotImpl(Path path, byte[] prolog, long start, long length, HashingAlgorithm<byte[]> hashing) throws IOException {
		if (start < 0)
			throw new IllegalArgumentException("the plot starting number cannot be negative");
		
		if (length < 1)
			throw new IllegalArgumentException("the plot length must be positive");

		if (prolog == null)
			throw new NullPointerException("the prolog cannot be null");

		if (prolog.length > MAX_PROLOG_SIZE)
			throw new IllegalArgumentException("the maximal prolog size is " + MAX_PROLOG_SIZE);

		this.prolog = prolog.clone();
		this.start = start;
		this.length = length;
		this.hashing = hashing;

		new Dumper(path);

		this.reader = new RandomAccessFile(path.toFile(), "r");
		this.channel = reader.getChannel();
	}

	private class Dumper {
		private final FileChannel channel;
		private final int nonceSize = Nonce.size(hashing);
		private final int metadataSize = getMetadataSize();
		private final long plotSize = metadataSize + length * nonceSize;

		private Dumper(Path where) throws IOException {
			long startTime = System.currentTimeMillis();
			logger.info("Starting creating a plot file of " + plotSize + " bytes");
		
			try (RandomAccessFile writer = new RandomAccessFile(where.toFile(), "rw");
				 FileChannel channel = this.channel = writer.getChannel()) {
		
				sizePlotFile();
				dumpMetadata();
				dumpNonces();
			}
			catch (UncheckedIOException e) {
				throw e.getCause();
			}
		
			logger.info("Plot file created in " + (System.currentTimeMillis() - startTime) + "ms");
		}

		/**
		 * Yields the size of the metadata reported before the nonces.
		 * 
		 * @return the size of the metadata
		 */
		private int getMetadataSize() {
			return 4 + prolog.length // prolog
				+ 8 // start
				+ 8 // length
				+ 4 + hashing.getName().getBytes(Charset.forName("UTF-8")).length; // hashing algorithm
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
			ByteBuffer buffer = ByteBuffer.wrap(new byte[1]);
			channel.write(buffer);
		}

		private void dumpMetadata() throws IOException {
			ByteBuffer buffer = ByteBuffer.allocate(metadataSize);
			buffer.putInt(prolog.length);
			buffer.put(prolog);
			buffer.putLong(start);
			buffer.putLong(length);
			byte[] name = hashing.getName().getBytes(Charset.forName("UTF-8"));
			buffer.putInt(name.length);
			buffer.put(name);

			try (var source = Channels.newChannel(new ByteArrayInputStream(buffer.array()))) {
				channel.transferFrom(source, 0L, metadataSize);
			}
		}

		private void dumpNonces() throws UncheckedIOException {
			LongStream.range(start, start + length)
				.parallel()
				.forEach(this::dumpNonce);
		}

		private void dumpNonce(long n) throws UncheckedIOException {
			try {
				// the hashing algorithm is cloned to avoid thread contention
				new NonceImpl(prolog, n, hashing.clone())
					.dumpInto(channel, metadataSize, n - start, length);
			}
			catch (IOException e) {
				throw new UncheckedIOException(e);
			}
		}
	}

	@Override
	public byte[] getProlog() {
		return prolog.clone();
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
	public Deadline getSmallestDeadline(int scoopNumber, byte[] data) throws IOException {
		if (scoopNumber < 0 || scoopNumber >= Nonce.SCOOPS_PER_NONCE)
			throw new IllegalArgumentException("illegal scoop number: it must be between 0 (inclusive) and " + Nonce.SCOOPS_PER_NONCE + " (exclusive)");

		try {
			return new SmallestDeadlineFinder(scoopNumber, data).deadline;
		}
		catch (UncheckedIOException e) {
			throw e.getCause();
		}
	}

	/**
	 * Yields the size of the metadata reported before the nonces.
	 * 
	 * @return the size of the metadata
	 */
	private int getMetadataSize() {
		return 4 + prolog.length // prolog
			+ 8 // start
			+ 8 // length
			+ 4 + hashing.getName().getBytes(Charset.forName("UTF-8")).length; // hashing algorithm
	}

	private static class DeadlineImpl implements Deadline {
		private final long progressive;
		private final byte[] value;
		
		private DeadlineImpl(long progressive, byte[] value) {
			this.progressive = progressive;
			this.value = value;
		}

		@Override
		public int compareTo(Deadline other) {
			byte[] left = value, right = other.getValue();

			for (int i = 0; i < left.length; i++) {
				int a = left[i] & 0xff;
				int b = right[i] & 0xff;
				if (a != b)
					return a - b;
			}

			return 0; // deadlines with the same hashing algorithm have the same length
		}

		@Override
		public long getProgressive() {
			return progressive;
		}

		@Override
		public byte[] getValue() {
			return value.clone();
		}
	}

	private class SmallestDeadlineFinder {
		private final int scoopNumber;
		private final Deadline deadline;
		private final int scoopSize = 2 * hashing.length();
		private final long groupSize = length * scoopSize;
		private final int metadataSize = getMetadataSize();

		private SmallestDeadlineFinder(int scoopNumber, byte[] data) {
			this.scoopNumber = scoopNumber;
			this.deadline = LongStream.range(start, start + length)
				.parallel()
				.mapToObj(n -> new DeadlineImpl(n, hashing.hash(concat(extractScoop(n - start), data))))
				.min(DeadlineImpl::compareTo)
				.get(); // OK, since the plot contains at least one nonce
		}

		private byte[] concat(byte[] array1, byte[] array2) {
			byte[] result = new byte[array1.length + array2.length];
			System.arraycopy(array1, 0, result, 0, array1.length);
			System.arraycopy(array2, 0, result, array1.length, array2.length);
			return result;
		}

		/**
		 * Extracts the scoop (two hashes) number {@code scoopNumber} from the nonce
		 * number {@code progressive} of this plot file.
		 * 
		 * @param progressive the progressive number of the nonce whose scoop must be extracted,
		 *                    between 0 (inclusive) and {@code length} (exclusive)
		 * @return the scoop data (two hashes)
		 */
		private byte[] extractScoop(long progressive) {
			try (var os = new ByteArrayOutputStream(); var destination = Channels.newChannel(os)) {
				// the scoop is inside its group, sequentially wrt the offset of the nonce
				channel.transferTo(metadataSize + scoopNumber * groupSize + progressive * scoopSize, scoopSize, destination);
				return os.toByteArray();
			}
			catch (IOException e) {
				throw new UncheckedIOException(e);
			}
		}
	}

	@Override
	public void close() throws IOException {
		try {
			reader.close();
		}
		catch (IOException e) {
			channel.close();
			throw e;
		}

		channel.close();
	}
}