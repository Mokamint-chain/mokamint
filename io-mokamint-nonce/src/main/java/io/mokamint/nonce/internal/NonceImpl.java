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

package io.mokamint.nonce.internal;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.channels.Channels;
import java.nio.channels.FileChannel;
import java.util.Objects;
import java.util.function.Function;

import io.hotmoka.annotations.Immutable;
import io.hotmoka.crypto.api.Hasher;
import io.hotmoka.crypto.api.HashingAlgorithm;
import io.mokamint.nonce.api.Challenge;
import io.mokamint.nonce.api.Nonce;
import io.mokamint.nonce.api.Prolog;

/**
 * A nonce of a plot file. Each nonce contains 4096 scoops.
 * Each scoop contains a pair of two hashes.
 * This code is a generalization and modification of the code
 * from {@url https://github.com/signum-network/signum-node/blob/main/src/brs/util/MiningPlot.java}.
 */
@Immutable
public final class NonceImpl implements Nonce {

	/**
	 * Generic data that identifies, for instance, the creator of the nonce.
	 */
	private final Prolog prolog;

	/**
	 * The hasher algorithm used for creating this nonce.
	 */
	private final Hasher<byte[]> hasher;

	/**
	 * The size of the hashes used to build this nonce.
	 */
	private final int hashSize;

	/**
	 * The data of the scoops inside the nonce.
	 */
	private final byte[] scoops;

	/**
	 * The progressive number of the nonce.
	 */
	private final long progressive;

	/**
	 * Creates the nonce for the given data and with the given number.
	 * 
	 * @param prolog generic data that identifies, for instance, the creator
	 *               of the nonce; this can be really anything but cannot be {@code null}
	 * @param progressive the progressive number of the nonce; this must be non-negative
	 * @param hashingForDeadlines the hashing algorithm to use to create the nonce
	 */
	public NonceImpl(Prolog prolog, long progressive, HashingAlgorithm hashingForDeadlines) {
		if (progressive < 0L)
			throw new IllegalArgumentException("progressive cannot be negative");

		this.prolog = Objects.requireNonNull(prolog, "prolog cannot be null");
		this.hasher = hashingForDeadlines.getHasher(Function.identity());
		this.hashSize = hashingForDeadlines.length();
		this.progressive = progressive;
		this.scoops = new Builder().scoops;
	}

	@Override
	public byte[] getValueFor(Challenge challenge) {
		byte[] generationSignature = challenge.getGenerationSignature();
		int scoopNumber = challenge.getScoopNumber();
		return hasher.hash(extractScoopAndConcat(scoopNumber, generationSignature));
	}

	@Override
	public void dumpInto(FileChannel where, int metadataSize, long offset, long length) throws IOException {
		int scoopSize = 2 * hashSize;

		// in order to get an optimized file, we put the scoops with the same number together,
		// inside a "group" of nonces:
		// the plot file contains groups of scoops: the group of first scoops in the nonces,
		// the group of the second scoops in the nonces, etc
		long groupSize = length * scoopSize;
		for (int scoopNumber = 0; scoopNumber < Challenge.SCOOPS_PER_NONCE; scoopNumber++)
			// scoopNumber * scoopSize is the position of scoopNumber inside the data of the nonce
			try (var source = Channels.newChannel(new ByteArrayInputStream(scoops, scoopNumber * scoopSize, scoopSize))) {
				// the scoop goes inside its group, sequentially wrt the offset of the nonce
				where.transferFrom(source, metadataSize + scoopNumber * groupSize + offset * scoopSize, scoopSize);
			}
	}

	/**
	 * Selects the given scoop from this nonce and adds the given generation signature at its end.
	 * 
	 * @param scoopNumber the number of the scoop to select
	 * @param generationSignature the generation signature to add after the scoop
	 * @return the concatenation of the scoop and the data
	 */
	private byte[] extractScoopAndConcat(int scoopNumber, byte[] generationSignature) {
		int scoopSize = hashSize * 2;
		var result = new byte[scoopSize + generationSignature.length];
		System.arraycopy(scoops, scoopNumber * scoopSize, result, 0, scoopSize);
		System.arraycopy(generationSignature, 0, result, scoopSize, generationSignature.length);
		return result;
	}

	/**
	 * The class that builds the nonce.
	 */
	private class Builder {
		
		private final static int HASH_CAP = 4096;

		/**
		 * The data of the scoops inside the nonce.
		 */
		private final byte[] scoops;

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

		private Builder() {
			this.scoopSize = 2 * hashSize;
			this.nonceSize = Challenge.SCOOPS_PER_NONCE * scoopSize;
			this.scoops = new byte[nonceSize];
			this.buffer = initWithPrologAndProgressive();
			fillWithScoops();
			applyFinalHash();
		}

		/**
		 * Computes the buffer for the computation of the nonce.
		 * This is an array, initially empty, that ends with the pairing of {@code prolog}
		 * followed by the big-endian representation of the non-negative {@code progressive}.
		 * 
		 * @return the initial seed
		 */
		private byte[] initWithPrologAndProgressive() {
			var prolog = NonceImpl.this.prolog.toByteArray();
			var buffer = new byte[nonceSize + prolog.length + 8];
			System.arraycopy(prolog, 0, buffer, nonceSize, prolog.length);
			longToBytesBE(progressive, buffer, nonceSize + prolog.length);
			return buffer;
		}

		private static void longToBytesBE(long l, byte[] target, int offset) {
	        offset += 7;
	        for (int i = 0; i <= 7; i++)
	            target[offset - i] = (byte) ((l>>(8*i)) & 0xFF);
	    }

		/**
		 * Fills the {@code buffer} with scoops. Initially, the buffer contains only
		 * a prolog at its end. This method will add scoops before that prolog.
		 */
		private void fillWithScoops() {
			var previous = new byte[32];
			for (int i = nonceSize; i > 0; i -= hashSize) {
				int len = Math.min(buffer.length - i, HASH_CAP);
				// we use a floating hashing window of size HASH_CAP instead of hashing always
				// the first HASH_CAP initialized bytes of the buffer;
				// this is a deviation from Signum's algorithm that forces
				// to keep all hashes in memory during the computation, not just the latest ones
				int offset;
				if (HASH_CAP < buffer.length - i) {
					long previousLong = previousAsLong(previous);
					offset = (int) (previousLong  % (buffer.length - i - HASH_CAP));
				}
				else
					offset = 0;

				// if + offset is removed below, then the algorithm becomes that of Signum
				System.arraycopy(previous = hasher.hash(buffer, i + offset, len), 0, buffer, i - hashSize, hashSize);
			}
		}

		private long previousAsLong(byte[] previous) {
			int b3 = 3 < hashSize ? (previous[3]&0xFF) : 0;
			int b2 = 2 < hashSize ? (previous[2]&0xFF) : 0;
			int b1 = 1 < hashSize ? (previous[1]&0xFF) : 0;
			int b0 = 0 < hashSize ? (previous[0]&0xFF) : 0;
			return b0 + (b1 + (b2 + b3 * 256L) * 256L) * 256L;
		}

		/**
		 * Computes the hash of the whole buffer and applies it to all scoops, in exclusive or.
		 */
		private void applyFinalHash() {
			byte[] finalHash = hasher.hash(buffer);
			
			// how much odd hashes must be shifted forward in order to fulfill the PoC2 representation
			int shiftForOdds = nonceSize + scoopSize;

			for (int i = 0; i < nonceSize; i++) {
				if (i % scoopSize == 0)
					shiftForOdds -= 2 * scoopSize;

				int hashNumber = i / hashSize;
				// we apply PoC2 reordering:
				if (hashNumber % 2 == 0)
					// even hash numbers remain at their place
					scoops[i] = (byte) (buffer[i] ^ finalHash[i % hashSize]);
				else
					scoops[i + shiftForOdds] = (byte) (buffer[i] ^ finalHash[i % hashSize]);
			}
		}
	}
}