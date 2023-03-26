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

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.io.UncheckedIOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.util.logging.Logger;
import java.util.stream.LongStream;

import io.hotmoka.crypto.HashingAlgorithm;
import io.hotmoka.spacemint.plotter.Nonce;
import io.hotmoka.spacemint.plotter.PlotCreator;

/**
 * Implementation of the creator of a plot file. A plot file contains sequential nonces.
 */
public class PlotCreatorImpl implements PlotCreator {
	private final static Logger logger = Logger.getLogger(PlotCreatorImpl.class.getName());
	private final byte[] prolog;
	private final long start;
	private final long length;
	private final HashingAlgorithm<byte[]> hashing;

	/**
	 * Creates a plot containing sequential nonces for the given data.
	 * 
	 * @param where the file where the plot must be dumped
	 * @param prolog generic data that identifies, for instance, the creator
	 *               of the nonces. This can be really anything but cannot be {@code null}
	 * @param start the starting progressive number of the nonces. This must be non-negative
	 * @param length the number of nonces to generate. This must be non-negative
	 * @param hashing the hashing algorithm to use to create the nonces
	 * @throws IOException if the plot could not be written into {@code where}
	 */
	public PlotCreatorImpl(File where, byte[] prolog, long start, long length, HashingAlgorithm<byte[]> hashing) throws IOException {
		if (start < 0)
			throw new IllegalArgumentException("the plot starting number cannot be negative");
		
		if (length < 0)
			throw new IllegalArgumentException("the plot length cannot be negative");

		this.prolog = prolog.clone();
		this.start = start;
		this.length = length;
		this.hashing = hashing;

		new Dumper(where);
	}

	private class Dumper {
		private final FileChannel channel;
		private final int nonceSize = Nonce.size(hashing);
		private final long plotSize = length * nonceSize;

		private Dumper(File where) throws IOException {
			long startTime = System.currentTimeMillis();
			logger.info("Starting creating a plot file of " + plotSize + " bytes");
		
			try (RandomAccessFile writer = new RandomAccessFile(where, "rw");
				 FileChannel channel = this.channel = writer.getChannel()) {
		
				sizePlotFile();
				mkNonces();
			}
			catch (UncheckedIOException e) {
				throw e.getCause();
			}
		
			logger.info("Plot file created in " + (System.currentTimeMillis() - startTime) + "ms");
		}

		/**
		 * Initializes the plot file: it is created at its final size, initially containing random bytes.
		 * 
		 * @throws IOException if the file cannot be initialized
		 */
		private void sizePlotFile() throws IOException {
			if (plotSize > 0) {
				// by forcing a write to the last byte of the file, we guarantee
				// that it is fully created (but randomly initialized, for now)
				channel.position(plotSize - 1);
				ByteBuffer buffer = ByteBuffer.wrap(new byte[1]);
				channel.write(buffer);
			}
		}

		private void mkNonces() throws UncheckedIOException {
			LongStream.range(start, start + length)
				.parallel()
				.forEach(this::mkNonce);
		}

		private void mkNonce(long n) throws UncheckedIOException {
			try {
				// the hashing algorithm is cloned to avoid thread contention
				new NonceImpl(prolog, n, hashing.clone())
					.dumpInto(channel, n - start, length);
			}
			catch (IOException e) {
				throw new UncheckedIOException(e);
			}
		}
	}
}