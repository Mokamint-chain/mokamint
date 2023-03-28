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

import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.channels.FileChannel;
import java.nio.charset.Charset;
import java.nio.file.Path;
import java.security.NoSuchAlgorithmException;

import io.hotmoka.crypto.HashingAlgorithm;
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
		this.prolog = new byte[prologLength];
		if (reader.read(prolog) != prologLength)
			throw new IOException("cannot read the prolog of the plot file");

		this.start = reader.readLong();
		this.length = reader.readLong();

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
	public void close() throws Exception {
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
