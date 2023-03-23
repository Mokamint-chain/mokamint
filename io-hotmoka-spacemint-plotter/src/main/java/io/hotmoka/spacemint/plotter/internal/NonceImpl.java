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

import java.math.BigInteger;

import io.hotmoka.crypto.HashingAlgorithm;
import io.hotmoka.spacemint.plotter.Nonce;

/**
 * A nonce of a plot file. Each nonce contains 4096 scoops.
 * Each scoop contains a pair of two 32 bytes hashes.
 * This code is a generalization and modification of the code
 * from {@url https://github.com/signum-network/signum-node/blob/main/src/brs/util/MiningPlot.java}.
 */
public class NonceImpl implements Nonce {

	private final byte[] data = new byte[NONCE_SIZE];

	/**
	 * Creates the nonce for the given data and with the given number.
	 * 
	 * @param prolog generic data that identifies, for instance, the creator
	 *               of the nonce. This can be really anything but cannot be {@code null}
	 * @param progressive the progressive number of the nonce. This must be non-negative
	 */
	public NonceImpl(byte[] prolog, BigInteger progressive) {
		if (progressive.signum() < 0)
			throw new IllegalArgumentException("nonce number cannot be negative");

		HashingAlgorithm<byte[]> shabal256 = HashingAlgorithm.shabal256((byte[] bytes) -> bytes);

		byte[] initialSeed = computeInitialSeed(prolog, progressive);
		byte[] buffer = new byte[NONCE_SIZE + initialSeed.length];
		System.arraycopy(initialSeed, 0, buffer, NONCE_SIZE, initialSeed.length);
		for (int i = NONCE_SIZE; i > 0; i -= HASH_SIZE) {
			int len = NONCE_SIZE + initialSeed.length - i;
			if (len > HASH_CAP)
				len = HASH_CAP;

			System.arraycopy(shabal256.hash(buffer, i, len), 0, buffer, i - HASH_SIZE, HASH_SIZE);
		}
		byte[] finalHash = shabal256.hash(buffer);
		for (int i = 0; i < NONCE_SIZE; i++)
			data[i] = (byte) (buffer[i] ^ finalHash[i % HASH_SIZE]);

		//PoC2 Rearrangement
		byte[] hashBuffer = new byte[HASH_SIZE];
		int halfSize = NONCE_SIZE / 2;
		for (int pos = HASH_SIZE, //Start at second hash in first scoop
				 revPos = NONCE_SIZE - HASH_SIZE; //Start at second hash in last scoop
			 pos < halfSize;
			 pos += SCOOP_SIZE,
			 revPos -= 64) { // move backwards
			System.arraycopy(data, pos, hashBuffer, 0, HASH_SIZE); //Copy low scoop second hash to buffer
			System.arraycopy(data, revPos, data, pos, HASH_SIZE); //Copy high scoop second hash to low scoop second hash
			System.arraycopy(hashBuffer, 0, data, revPos, HASH_SIZE); //Copy buffer to high scoop second hash
		}
		
		BigInteger sum = BigInteger.ZERO;
		for (byte b: data) {
			System.out.println(b);
			sum = sum.add(BigInteger.valueOf(b));
		}
		System.out.println("sum = " + sum);
	}

	/**
	 * Computes the initial seed of the computation of the nonce.
	 * This is the pairing of {@code prolog} followed by the
	 * big-endian representation of the non-negative {@code progressive}.
	 * 
	 * @param prolog the prolog of the nonce
	 * @param progressive the progressive number of the nonce
	 * @return the initial seed
	 */
	private static byte[] computeInitialSeed(byte[] prolog, BigInteger progressive) {
		byte[] progressiveAsBytes = progressive.toByteArray();

		byte[] seed;
		if (progressiveAsBytes[0] == (byte) 0) {
			// we do not consider the first byte if it is 0, since it is just the sign bit,
			// irrelevant for an unsigned progressive
			seed = new byte[prolog.length + progressiveAsBytes.length - 1];
			System.arraycopy(prolog, 0, seed, 0, prolog.length);
			System.arraycopy(progressiveAsBytes, 1, seed, prolog.length, progressiveAsBytes.length - 1);
		}
		else {
			seed = new byte[prolog.length + progressiveAsBytes.length];
			System.arraycopy(prolog, 0, seed, 0, prolog.length);
			System.arraycopy(progressiveAsBytes, 0, seed, prolog.length, progressiveAsBytes.length);
		}

		return seed;
	}
	
	private static final int HASH_SIZE = 32;
	private static final int HASHES_PER_SCOOP = 2;
	public static final int SCOOP_SIZE = HASHES_PER_SCOOP * HASH_SIZE; // 64
	public static final int SCOOPS_PER_NONCE = 4096;
	public static final int NONCE_SIZE = SCOOPS_PER_NONCE * SCOOP_SIZE; // 4096 * 64
	private static final int HASH_CAP = 4096;
}