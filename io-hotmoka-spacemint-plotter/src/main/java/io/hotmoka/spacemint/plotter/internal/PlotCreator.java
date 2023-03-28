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
import java.io.IOException;
import java.io.RandomAccessFile;
import java.io.UncheckedIOException;
import java.nio.ByteBuffer;
import java.nio.channels.Channels;
import java.nio.channels.FileChannel;
import java.nio.charset.Charset;
import java.nio.file.Path;
import java.util.logging.Logger;
import java.util.stream.LongStream;

import io.hotmoka.crypto.HashingAlgorithm;
import io.hotmoka.spacemint.plotter.Nonce;
import io.hotmoka.spacemint.plotter.Plot;

/**
 * Implementation of the creator of a plot file. A plot file contains sequential nonces.
 */
class PlotCreator {
	private final static Logger logger = Logger.getLogger(PlotCreator.class.getName());
	private final byte[] prolog;
	private final long start;
	private final long length;
	private final HashingAlgorithm<byte[]> hashing;

	/**
	 * Creates a plot containing sequential nonces for the given prolog.
	 * 
	 * @param path the path to the file where the plot must be dumped
	 * @param prolog generic data that identifies, for instance, the creator
	 *               of the plot. This can be really anything but cannot be {@code null}
	 * @param start the starting progressive number of the nonces in the plot.
	 *              This must be non-negative
	 * @param length the number of nonces to generate. This must be non-negative
	 * @param hashing the hashing algorithm to use for creating the nonces
	 * @throws IOException if the plot could not be written into {@code path}
	 */
	PlotCreator(Path path, byte[] prolog, long start, long length, HashingAlgorithm<byte[]> hashing) throws IOException {
		if (start < 0)
			throw new IllegalArgumentException("the plot starting number cannot be negative");
		
		if (length < 0)
			throw new IllegalArgumentException("the plot length cannot be negative");

		if (prolog == null)
			throw new NullPointerException("the prolog cannolt be null");

		if (prolog.length > Plot.MAX_PROLOG_SIZE)
			throw new IllegalArgumentException("the maximal prolog size is " + Plot.MAX_PROLOG_SIZE);

		this.prolog = prolog.clone();
		this.start = start;
		this.length = length;
		this.hashing = hashing;

		new Dumper(path);
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
		 * Yields the size of the metadata reported before the nonces
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
}