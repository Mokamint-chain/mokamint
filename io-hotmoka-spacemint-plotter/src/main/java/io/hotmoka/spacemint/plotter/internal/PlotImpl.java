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

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.io.UncheckedIOException;
import java.nio.channels.Channels;
import java.nio.channels.FileChannel;
import java.nio.charset.Charset;
import java.nio.file.Path;
import java.security.NoSuchAlgorithmException;
import java.util.stream.LongStream;

import io.hotmoka.crypto.HashingAlgorithm;
import io.hotmoka.spacemint.plotter.Nonce;
import io.hotmoka.spacemint.plotter.Plot;

/**
 */
public class PlotImpl implements Plot {
	
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
		if (prologLength > Plot.MAX_PROLOG_SIZE)
			throw new IllegalArgumentException("the maximal prolog size is " + Plot.MAX_PROLOG_SIZE);
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
	 * Creates a plot file containing sequential nonces for the given prolog.
	 * 
	 * @param path the path to the file where the plot must be dumped
	 * @param prolog generic data that identifies, for instance, the creator
	 *               of the plot. This can be really anything but cannot be {@code null}
	 * @param start the starting progressive number of the nonces in the plot.
	 *              This must be non-negative
	 * @param length the number of nonces to generate. This must be non-negative
	 * @param hashing the hashing algorithm to use for creating the nonces
	 * @return the plot that has been created
	 * @throws IOException if the plot file could not be written into {@code path}
	 */
	public static PlotImpl create(Path path, byte[] prolog, long start, long length, HashingAlgorithm<byte[]> hashing) throws IOException {
		new PlotCreator(path, prolog, start, length, hashing);

		try {
			return new PlotImpl(path);
		}
		catch (NoSuchAlgorithmException e) {
			// unexpected: we just created the file with the same algorithm, so it must exist
			throw new IllegalStateException("unexpcted missing algorithm", e);
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
	public Nonce getNonceWithSmallestDeadline(int scoopNumber, byte[] data) {
		if (scoopNumber < 0 || scoopNumber >= Nonce.SCOOPS_PER_NONCE)
			throw new IllegalArgumentException("illegal scoop number: it must be between 0 (inclusive) and " + Nonce.SCOOPS_PER_NONCE + " (exclusive)");

		long start = System.currentTimeMillis();
		long bestProgressive = new ProgressiveOfNonceWithSmallestDeadline(scoopNumber, data).progressive;
		System.out.println(System.currentTimeMillis() - start + "ms");

		System.out.println(bestProgressive);

		return null;
	}

	private static class IndexedHash implements Comparable<IndexedHash> {
		private final long progressive;
		private final byte[] hash;
		
		private IndexedHash(long progressive, byte[] hash) {
			this.progressive = progressive;
			this.hash = hash;
		}

		@Override
		public int compareTo(IndexedHash other) {
			byte[] left = hash, right = other.hash;

			for (int i = 0; i < left.length; i++) {
				int a = (left[i] & 0xff);
				int b = (right[i] & 0xff);
				if (a != b)
					return a - b;
			}

			return 0; // hashes with the same algorithm have the same length
		}
	}

	private class ProgressiveOfNonceWithSmallestDeadline {
		private final int scoopNumber;
		private final long progressive;
		private final int scoopSize = 2 * hashing.length();
		private final long groupSize = length * scoopSize;
		private final int metadataSize = getMetadataSize();

		private ProgressiveOfNonceWithSmallestDeadline(int scoopNumber, byte[] data) { // TODO
			this.scoopNumber = scoopNumber;

			progressive = LongStream.range(start, start + length)
				.parallel()
				.mapToObj(n -> new IndexedHash(n, hashing.hash(concat(extractScoop(n), data))))
				.min(IndexedHash::compareTo)
				.get() // OK, since the plot contains at least a nonce
				.progressive;
		}

		private byte[] concat(byte[] array1, byte[] array2) {
			byte[] result = new byte[array1.length + array2.length];
			System.arraycopy(array1, 0, result, 0, array1.length);
			System.arraycopy(array2, 0, result, array1.length, array2.length);
			return result;
		}

		/**
		 * @param progressive1
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
