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
import java.nio.channels.Channels;
import java.nio.channels.FileChannel;

import io.hotmoka.crypto.HashingAlgorithm;
import io.hotmoka.spacemint.plotter.Nonce;

/**
 * A nonce of a plot file. Each nonce contains 4096 scoops.
 * Each scoop contains a pair of two hashes.
 * This code is a generalization and modification of the code
 * from {@url https://github.com/signum-network/signum-node/blob/main/src/brs/util/MiningPlot.java}.
 */
public class NonceImpl implements Nonce {
	private final int hashSize;
	private final byte[] data;
	private final static int HASH_CAP = 4096;

	/**
	 * Creates the nonce for the given data and with the given number.
	 * 
	 * @param prolog generic data that identifies, for instance, the creator
	 *               of the nonce. This can be really anything but cannot be {@code null}
	 * @param progressive the progressive number of the nonce. This must be non-negative
	 * @param hashing the hashing algorithm to use to create the nonce
	 */
	public NonceImpl(byte[] prolog, long progressive, HashingAlgorithm<byte[]> hashing) {
		if (progressive < 0L)
			throw new IllegalArgumentException("nonce number cannot be negative");

		this.hashSize = hashing.length();
		this.data = new Builder(prolog, progressive, hashing).data;
	}

	/**
	 * Dumps this nonce into the give file, as the {@code offset}th nonce inside the file.
	 * 
	 * @param where the file channel where the nonce must be dumped
	 * @param offset the progressive number of the nonce inside the file; for instance,
	 *               if the file will contains nonces from progressive 100 to progressive 150,
	 *               then they are placed at offsets from 0 to 50 inside the file, respectively
	 * @param length the total number of nonces that will be contained in the file
	 * @throws IOException if the nonce could not be dumped into the file
	 */
	void dumpInto(FileChannel where, long offset, long length) throws IOException {
		int scoopSize = 2 * hashSize;

		// in order to get an optimized file, we put the scoops with the same number together,
		// inside a "group" of nonces:
		// the plot file contains groups of scoops: the group of first scoops in the nonces,
		// the group of the second scoops in the nonces, etc
		long groupSize = length * scoopSize;
		for (int scoopNumber = 0; scoopNumber < SCOOPS_PER_NONCE; scoopNumber++)
			// scoopNumber * scoopSize is the position of scoopNumber inside the data of the nonce
			try (var source = Channels.newChannel(new ByteArrayInputStream(data, scoopNumber * scoopSize, scoopSize))) {
				// the scoop goes inside its group, sequentially wrt the offset of the nonce
				where.transferFrom(source, scoopNumber * groupSize + offset * scoopSize, scoopSize);
			}
	}


	/**
	 * The class that builds the nonce.
	 */
	private class Builder {
		private final HashingAlgorithm<byte[]> hashing;
		
		/**
		 * The data of the nonce.
		 */
		private final byte[] data;

		/**
		 * The size of the nonce. This is {@code data.length}.
		 */
		private final int nonceSize;

		/**
		 * The size of a scoop (two hashes).
		 */
		private final int scoopSize;

		/**
		 * Temporary data of the nonce. This is {@code data} followed by
		 * the prolog and progressive of the nonce.
		 */
		private final byte[] buffer;

		private Builder(byte[] prolog, long progressive, HashingAlgorithm<byte[]> hashing) {
			this.hashing = hashing;
			this.data = new byte[SCOOPS_PER_NONCE * 2 * hashSize];
			this.nonceSize = data.length;
			this.scoopSize = 2 * hashSize;
			this.buffer = initWithPrologAndProgressive(prolog, progressive);
			fillWithScoops();
			applyFinalHash();
		}

		/**
		 * Computes the buffer for the computation of the nonce.
		 * This is an array, initially empty, that ends with the pairing of {@code prolog}
		 * followed by the big-endian representation of the non-negative {@code progressive}.
		 * 
		 * @param prolog the prolog of the nonce
		 * @param progressive the progressive number of the nonce
		 * @return the initial seed
		 */
		private byte[] initWithPrologAndProgressive(byte[] prolog, long progressive) {
			byte[] buffer = new byte[nonceSize + prolog.length + 8];
			System.arraycopy(prolog, 0, buffer, nonceSize, prolog.length);
			longToBytesBE(progressive, buffer, nonceSize + prolog.length);
			return buffer;
		}

		private void longToBytesBE(long l, byte[] target, int offset) {
	        offset += 7;
	        for (int i = 0; i <= 7; i++)
	            target[offset - i] = (byte)((l>>(8*i)) & 0xFF);
	    }

		/**
		 * Fills the {@code buffer} with scoops. Initially, the buffer contains only
		 * a prolog at its end. This method will add scoops before that prolog.
		 */
		private void fillWithScoops() {
			for (int i = nonceSize; i > 0; i -= hashSize) {
				int len = Math.min(buffer.length - i, HASH_CAP);
				System.arraycopy(hashing.hash(buffer, i, len), 0, buffer, i - hashSize, hashSize);
			}
		}

		/**
		 * Computes the hash of the whole buffer and applies it to all scoops, in exclusive or.
		 */
		private void applyFinalHash() {
			byte[] finalHash = hashing.hash(buffer);
			
			// how much odds hashes should be shifted forward in order to
			// fulfill the PoC2 representation
			int shiftForOdds = nonceSize + scoopSize;

			for (int i = 0; i < nonceSize; i++) {
				if (i % scoopSize == 0)
					shiftForOdds -= 2 * scoopSize;

				int hashNumber = i / hashSize;
				// we apply PoC2 reordering:
				if (hashNumber % 2 == 0)
					// even hash numbers remain at their place
					data[i] = (byte) (buffer[i] ^ finalHash[i % hashSize]);
				else
					data[i + shiftForOdds] = (byte) (buffer[i] ^ finalHash[i % hashSize]);
			}
		}

		/**
		 * Moves hashes in order to reach the PoC2 format of a nonce.
		 */
		/*private void rearrangeInPoC2Format() {
			byte[] hashBuffer = new byte[hashSize];
			int halfSize = nonceSize / 2;

			// left starts at the second hash in the first scoop and moves rightwards
			// right starts at the second hash in the last scoop and moves leftwards
			for (int left = hashSize, right = nonceSize - hashSize; left < halfSize; left += scoopSize, right -= scoopSize) {
				System.arraycopy(data, left, hashBuffer, 0, hashSize); // copy low scoop second hash to buffer
				System.arraycopy(data, right, data, left, hashSize); // copy high scoop second hash to low scoop second hash
				System.arraycopy(hashBuffer, 0, data, right, hashSize); // copy buffer to high scoop second hash
			}
		}*/
	}
}