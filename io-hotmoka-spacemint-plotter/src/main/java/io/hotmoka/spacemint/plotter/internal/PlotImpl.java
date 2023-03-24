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

import static java.math.BigInteger.ONE;

import java.math.BigInteger;
import java.util.stream.Stream;

import io.hotmoka.crypto.HashingAlgorithm;
import io.hotmoka.spacemint.plotter.Plot;

/**
 */
public class PlotImpl implements Plot {

	/**
	 * Creates a plot containing sequential nonces for the given data.
	 * 
	 * @param prolog generic data that identifies, for instance, the creator
	 *               of the nonces. This can be really anything but cannot be {@code null}
	 * @param start the starting progressive number of the nonces. This must be non-negative
	 * @param length the length of the sequence of nonces to generate. This must be non-negative
	 * @param hashing the hashing algorithm to use to create the nonces
	 */
	public PlotImpl(byte[] prolog, BigInteger start, BigInteger length, HashingAlgorithm<byte[]> hashing) {
		if (start.signum() < 0)
			throw new IllegalArgumentException("the plot starting number cannot be negative");
		
		if (length.signum() < 0)
			throw new IllegalArgumentException("the plot length cannot be negative");

		Stream.iterate(start, n -> n.subtract(start).compareTo(length) < 0, ONE::add)
			.forEach(n -> new NonceImpl(prolog, n, hashing));
	}
}