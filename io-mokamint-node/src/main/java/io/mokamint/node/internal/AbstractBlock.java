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

package io.mokamint.node.internal;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.math.BigInteger;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;

import io.hotmoka.crypto.api.HashingAlgorithm;
import io.hotmoka.marshalling.AbstractMarshallable;
import io.hotmoka.marshalling.UnmarshallingContexts;
import io.hotmoka.marshalling.api.UnmarshallingContext;
import io.mokamint.node.api.Block;
import io.mokamint.node.api.DatabaseException;
import io.mokamint.node.api.GenesisBlock;
import io.mokamint.nonce.DeadlineDescriptions;
import io.mokamint.nonce.api.Deadline;
import io.mokamint.nonce.api.DeadlineDescription;

/**
 * Shared code of all classes implementing blocks.
 */
public abstract class AbstractBlock extends AbstractMarshallable {

	private final static BigInteger SCOOPS_PER_NONCE = BigInteger.valueOf(Deadline.MAX_SCOOP_NUMBER + 1);

	/**
	 * Unmarshals a block from the given context.
	 * 
	 * @param context the context
	 * @return the block
	 * @throws NoSuchAlgorithmException if the hashing algorithm of the block is unknown
	 * @throws IOException if the block cannot be unmarshalled
	 */
	public static Block from(UnmarshallingContext context) throws NoSuchAlgorithmException, IOException {
		// by reading the height, we can determine if it's a genesis block or not
		var height = context.readLong();
		if (height == 0L)
			return new GenesisBlockImpl(context);
		else if (height > 0L)
			return new NonGenesisBlockImpl(height, context);
		else
			throw new IOException("negative block height");
	}

	/**
	 * Unmarshals a block from the given bytes.
	 * 
	 * @param bytes the bytes
	 * @return the block
	 * @throws NoSuchAlgorithmException if the hashing algorithm of the block is unknown
	 * @throws IOException if the block could not be unmarshalled
	 */
	public static Block from(byte[] bytes) throws NoSuchAlgorithmException, IOException {
		try (var bais = new ByteArrayInputStream(bytes); var context = UnmarshallingContexts.of(bais)) {
			return from(context);
		}
	}

	public final DeadlineDescription getNextDeadlineDescription(HashingAlgorithm<byte[]> hashingForGenerations, HashingAlgorithm<byte[]> hashingForDeadlines) {
		var nextGenerationSignature = getNextGenerationSignature(hashingForGenerations);

		return DeadlineDescriptions.of
			(getNextScoopNumber(nextGenerationSignature, hashingForGenerations), nextGenerationSignature, hashingForDeadlines);
	}

	public final byte[] getHash(HashingAlgorithm<byte[]> hashing) {
		return hashing.hash(toByteArray());
	}

	/**
	 * Yields the height of the block, counting from 0 for the genesis block.
	 * 
	 * @return the height of the block
	 */
	public abstract long getHeight();

	/**
	 * Yields the total waiting time, in milliseconds, from the genesis block
	 * until the creation of this block.
	 * 
	 * @return the total waiting time
	 */
	public abstract long getTotalWaitingTime();

	public LocalDateTime getCreationTime(GenesisBlock genesis) {
		return genesis.getStartDateTimeUTC().plus(getTotalWaitingTime(), ChronoUnit.MILLIS);
	}

	private int getNextScoopNumber(byte[] nextGenerationSignature, HashingAlgorithm<byte[]> hashing) {
		var generationHash = hashing.hash(concat(nextGenerationSignature, longToBytesBE(getHeight() + 1)));
		return new BigInteger(1, generationHash).remainder(SCOOPS_PER_NONCE).intValue();
	}

	protected abstract byte[] getNextGenerationSignature(HashingAlgorithm<byte[]> hashing);

	protected static byte[] concat(byte[] array1, byte[] array2) {
		var merge = new byte[array1.length + array2.length];
		System.arraycopy(array1, 0, merge, 0, array1.length);
		System.arraycopy(array2, 0, merge, array1.length, array2.length);
		return merge;
	}

	private static byte[] longToBytesBE(long l) {
		var target = new byte[8];

		for (int i = 0; i <= 7; i++)
			target[7 - i] = (byte) ((l>>(8*i)) & 0xFF);

		return target;
	}
}