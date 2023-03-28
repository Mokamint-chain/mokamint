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

package io.hotmoka.spacemint.plotter;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import io.hotmoka.crypto.HashingAlgorithm;
import io.hotmoka.spacemint.plotter.internal.PlotImpl;

/**
 */
public interface Plot extends AutoCloseable {

	static Plot load(Path path) throws IOException {
		return new PlotImpl(path);
	}

	static Plot create(Path path, byte[] prolog, long start, long length, HashingAlgorithm<byte[]> hashing) throws IOException {
		return PlotImpl.create(path, prolog, start, length, hashing);
	}

	public static void main(String[] args) throws IOException {
		Path path = Paths.get("pippo.plot");
		Files.deleteIfExists(path);
		create(path, new byte[] { 11, 13, 24, 88 }, 65536L, 100L, HashingAlgorithm.shabal256((byte[] bytes) -> bytes));
	}
}
