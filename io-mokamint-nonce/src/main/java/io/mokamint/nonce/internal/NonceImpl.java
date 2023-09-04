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

import io.hotmoka.crypto.api.HashingAlgorithm;
import io.mokamint.nonce.Deadlines;
import io.mokamint.nonce.api.Deadline;
import io.mokamint.nonce.api.DeadlineDescription;
import io.mokamint.nonce.api.Nonce;

/**
 * A nonce of a plot file. Each nonce contains 4096 scoops.
 * Each scoop contains a pair of two hashes.
 * This code is a generalization and modification of the code
 * from {@url https://github.com/signum-network/signum-node/blob/main/src/brs/util/MiningPlot.java}.
 */
public class NonceImpl implements Nonce {

	/**
	 * Generic data that identifies, for instance, the creator
	 * of the nonce. This can be really anything.
	 */
	private final byte[] prolog;

	/**
	 * The hashing algorithm used for creating this nonce.
	 */
	private final HashingAlgorithm<byte[]> hashing;

	private final int hashSize;
	private final byte[] data;

	/**
	 * The progressive number of the nonce.
	 */
	private final long progressive;

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
		Objects.requireNonNull(prolog, "prolog cannot be null");
		Objects.requireNonNull(hashing, "hashing cannot be null");

		if (progressive < 0L)
			throw new IllegalArgumentException("progressive cannot be negative");

		if (prolog.length > Deadline.MAX_PROLOG_SIZE)
			throw new IllegalArgumentException("Illegal prolog size: the maximum is " + Deadline.MAX_PROLOG_SIZE);
			
		this.prolog = prolog;
		this.hashing = hashing;
		this.hashSize = hashing.length();
		this.progressive = progressive;
		this.data = new Builder().data;
	}

	@Override
	public Deadline getDeadline(DeadlineDescription description) {
		if (!description.getHashing().getName().equals(hashing.getName()))
			throw new IllegalArgumentException("deadline description and nonce use different hashing algorithms");

		byte[] value = hashing.hash(extractScoopAndConcat(description.getScoopNumber(), description.getData()));
		return Deadlines.of(prolog, progressive, value, description.getScoopNumber(), description.getData(), hashing);
	}

	/**
	 * Selects the given scoop from this nonce and adds the given data at its end.
	 * 
	 * @param scoopNumber the number of the scoop to select, between 0 (inclusive) and
	 *                    {@link Deadline#MAX_SCOOP_NUMBER} (inclusive)
	 * @param data the data to end after the scoop
	 * @return the concatenation of the scoop and the data
	 */
	private byte[] extractScoopAndConcat(int scoopNumber, byte[] data) {
		int scoopSize = hashSize * 2;
		var result = new byte[scoopSize + data.length];
		System.arraycopy(this.data, scoopNumber * scoopSize, result, 0, scoopSize);
		System.arraycopy(data, 0, result, scoopSize, data.length);
		return result;
	}

	/**
	 * Dumps this nonce into the give file, as the {@code offset}th nonce inside the file.
	 * 
	 * @param where the file channel where the nonce must be dumped
	 * @param metadataSize the size of the metadata of the plot file, that precedes the nonces
	 * @param offset the progressive number of the nonce inside the file; for instance,
	 *               if the file will contains nonces from progressive 100 to progressive 150,
	 *               then they are placed at offsets from 0 to 50 inside the file, respectively
	 * @param length the total number of nonces that will be contained in the file
	 * @throws IOException if the nonce could not be dumped into the file
	 */
	public void dumpInto(FileChannel where, int metadataSize, long offset, long length) throws IOException {
		int scoopSize = 2 * hashSize;

		// in order to get an optimized file, we put the scoops with the same number together,
		// inside a "group" of nonces:
		// the plot file contains groups of scoops: the group of first scoops in the nonces,
		// the group of the second scoops in the nonces, etc
		long groupSize = length * scoopSize;
		for (int scoopNumber = 0; scoopNumber <= Deadline.MAX_SCOOP_NUMBER; scoopNumber++)
			// scoopNumber * scoopSize is the position of scoopNumber inside the data of the nonce
			try (var source = Channels.newChannel(new ByteArrayInputStream(data, scoopNumber * scoopSize, scoopSize))) {
				// the scoop goes inside its group, sequentially wrt the offset of the nonce
				where.transferFrom(source, metadataSize + scoopNumber * groupSize + offset * scoopSize, scoopSize);
			}
	}

	/**
	 * The class that builds the nonce.
	 */
	private class Builder {
		
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

		private Builder() {
			this.data = new byte[(Deadline.MAX_SCOOP_NUMBER + 1) * 2 * hashSize];
			this.nonceSize = data.length;
			this.scoopSize = 2 * hashSize;
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
			byte[] buffer = new byte[nonceSize + prolog.length + 8];
			System.arraycopy(prolog, 0, buffer, nonceSize, prolog.length);
			longToBytesBE(progressive, buffer, nonceSize + prolog.length);
			return buffer;
		}

		private void longToBytesBE(long l, byte[] target, int offset) {
	        offset += 7;
	        for (int i = 0; i <= 7; i++)
	            target[offset - i] = (byte) ((l>>(8*i)) & 0xFF);
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
			
			// how much odd hashes must be shifted forward in order to fulfill the PoC2 representation
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
	}
}