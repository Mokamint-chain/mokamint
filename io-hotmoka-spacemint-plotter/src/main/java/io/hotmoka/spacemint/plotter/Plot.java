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

import java.math.BigInteger;

import io.hotmoka.crypto.HashingAlgorithm;
import io.hotmoka.spacemint.plotter.internal.PlotImpl;

/**
 */
public interface Plot {

	/**
	 * Yields a plot containing sequential nonces for the given data.
	 * 
	 * @param prolog generic data that identifies, for instance, the creator
	 *               of the nonces. This can be really anything but cannot be {@code null}
	 * @param start the starting progressive number of the nonces. This must be non-negative
	 * @param length the length of the sequence of nonces to generate. This must be non-negative
	 * @param hashing the hashing algorithm to use to create the nonces
	 */
	public static Plot of(byte[] prolog, BigInteger start, BigInteger length, HashingAlgorithm<byte[]> hashing) {
		return new PlotImpl(prolog, start, length, hashing);
	}

	public static void main(String[] args) {
		of(new byte[] { 11, 13, 24, 88 }, BigInteger.valueOf(65536), BigInteger.ONE, HashingAlgorithm.shabal256((byte[] bytes) -> bytes));
	}
}