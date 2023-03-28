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
import java.nio.file.Path;

import io.hotmoka.crypto.HashingAlgorithm;
import io.hotmoka.spacemint.plotter.Plot;

/**
 */
public class PlotImpl implements Plot {
	private final RandomAccessFile reader;
	private final FileChannel channel;

	public PlotImpl(Path path) throws IOException {
		this.reader = new RandomAccessFile(path.toFile(), "r");
		this.channel = reader.getChannel();
	}

	public static PlotImpl create(Path path, byte[] prolog, long start, long length, HashingAlgorithm<byte[]> hashing) throws IOException {
		new PlotCreator(path, prolog, start, length, hashing);
		return new PlotImpl(path);
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
