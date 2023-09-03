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
import java.util.Objects;

import io.hotmoka.annotations.GuardedBy;
import io.hotmoka.crypto.Hex;
import io.hotmoka.crypto.api.HashingAlgorithm;
import io.hotmoka.marshalling.AbstractMarshallable;
import io.hotmoka.marshalling.UnmarshallingContexts;
import io.hotmoka.marshalling.api.UnmarshallingContext;
import io.mokamint.node.Blocks;
import io.mokamint.node.api.Block;
import io.mokamint.node.api.NonGenesisBlock;
import io.mokamint.nonce.DeadlineDescriptions;
import io.mokamint.nonce.api.Deadline;
import io.mokamint.nonce.api.DeadlineDescription;

/**
 * Shared code of all classes implementing blocks.
 */
public abstract class AbstractBlock extends AbstractMarshallable {

	/**
	 * A lock for the {@link #lastHash} and {@link #lastHashingName} fields.
	 */
	private final Object lock = new Object();

	/**
	 * The name of the hashing algorithm used for the last call to {@link #getHash(HashingAlgorithm)}, if any.
	 */
	@GuardedBy("lock")
	private String lastHashingName;

	/**
	 * The result of the last call to {@link #getHash(HashingAlgorithm)}.
	 */
	@GuardedBy("lock")
	private byte[] lastHash;

	private final static BigInteger SCOOPS_PER_NONCE = BigInteger.valueOf(Deadline.MAX_SCOOP_NUMBER + 1);

	private final static BigInteger _20 = BigInteger.valueOf(20L);

	private final static BigInteger _100 = BigInteger.valueOf(100L);

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

	/**
	 * Yields the hash of this block, by using the given hashing algorithm.
	 * 
	 * @param hashing the hashing algorithm
	 * @return the hash of this block
	 */
	public final byte[] getHash(HashingAlgorithm<byte[]> hashing) {
		// it uses a cache for optimization, since the computation might be expensive
	
		String name = hashing.getName();
	
		synchronized (lock) {
			if (Objects.equals(lastHashingName, name))
				return lastHash.clone();
		}
	
		byte[] result = hashing.hash(toByteArray());
	
		synchronized (lock) {
			lastHashingName = name;
			lastHash = result.clone();
		}
	
		return result;
	}

	/**
	 * Yields the hash of this block, by using the given hashing algorithm,
	 * as an hexadecimal string.
	 * 
	 * @param hashing the hashing algorithm
	 * @return the hash of this block, as a hexadecimal string
	 */
	public final String getHexHash(HashingAlgorithm<byte[]> hashing) {
		return Hex.toHexString(getHash(hashing));
	}

	public final DeadlineDescription getNextDeadlineDescription(HashingAlgorithm<byte[]> hashingForGenerations, HashingAlgorithm<byte[]> hashingForDeadlines) {
		var nextGenerationSignature = getNextGenerationSignature(hashingForGenerations);

		return DeadlineDescriptions.of
			(getNextScoopNumber(nextGenerationSignature, hashingForGenerations), nextGenerationSignature, hashingForDeadlines);
	}

	/**
	 * Yields the description of the next block, assuming that the latter has the given deadline.
	 * 
	 * @param deadline the deadline of the next block
	 * @param targetBlockCreationTime the target time interval, in milliseconds, between the creation of a block
	 *                                and the creation of a next block
	 * @param hashingForBlocks the hashing algorithm used for the blocks
	 * @return the description
	 */
	public final NonGenesisBlock getNextBlockDescription(Deadline deadline, long targetBlockCreationTime, HashingAlgorithm<byte[]> hashingForBlocks, HashingAlgorithm<byte[]> hashingForDeadlines) {
		var heightForNewBlock = getHeight() + 1;
		var powerForNewBlock = computePower(deadline, hashingForDeadlines);
		var waitingTimeForNewBlock = deadline.getMillisecondsToWaitFor(getAcceleration());
		var weightedWaitingTimeForNewBlock = computeWeightedWaitingTime(waitingTimeForNewBlock);
		var totalWaitingTimeForNewBlock = computeTotalWaitingTime(waitingTimeForNewBlock);
		var accelerationForNewBlock = computeAcceleration(weightedWaitingTimeForNewBlock, targetBlockCreationTime);
		var hashOfPreviousBlock = getHash(hashingForBlocks);

		return Blocks.of(heightForNewBlock, powerForNewBlock, totalWaitingTimeForNewBlock,
			weightedWaitingTimeForNewBlock, accelerationForNewBlock, deadline, hashOfPreviousBlock);
	}

	/**
	 * Yields the power of this block, computed as the sum, for each block from genesis to this,
	 * of 2^(hashing bits) / (value of the deadline in the block + 1). This allows one to compare
	 * forks and choose the one whose tip has the highest power. Intuitively, the power
	 * expresses the space used to compute the chain leading to the block.
	 * 
	 * @return the power
	 */
	public abstract BigInteger getPower();

	/**
	 * Yields the weighted waiting time, in milliseconds, from the genesis block
	 * until the creation of this block. This is an average waiting time that gives
	 * 5% weight to the waiting time for this block and 95% to the cumulative
	 * weighted waiting time at the previous block.
	 * 
	 * @return the weighted waiting time
	 */
	public abstract long getWeightedWaitingTime();

	/**
	 * Yields the acceleration used for the creation of this block, that is,
	 * a value used to divide the deadline to derive the time needed to wait for it.
	 * The higher, the shorter the time. This value changes from block to block in order
	 * to cope with varying mining power in the network. It is the inverse of Bitcoin's difficulty.
	 * 
	 * @return the acceleration
	 */
	public abstract BigInteger getAcceleration();

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

	private BigInteger computePower(Deadline deadline, HashingAlgorithm<byte[]> hashingForDeadlines) {
		byte[] valueAsBytes = deadline.getValue();
		var value = new BigInteger(1, valueAsBytes);
		return getPower().add(BigInteger.TWO.shiftLeft(hashingForDeadlines.length() * 8).divide(value.add(BigInteger.ONE)));
	}

	private long computeTotalWaitingTime(long waitingTime) {
		return getTotalWaitingTime() + waitingTime;
	}

	private long computeWeightedWaitingTime(long waitingTime) {
		var previousWeightedWaitingTime_95 = getWeightedWaitingTime() * 95L;
		var waitingTime_5 = waitingTime * 5L;
		return (previousWeightedWaitingTime_95 + waitingTime_5) / 100L;
	}

	/**
	 * Computes the acceleration for the new block, in order to get closer to the target creation time.
	 * 
	 * @param weightedWaitingTimeForNewBlock the weighted waiting time for the new block
	 * @param targetBlockCreationTime 
	 * @return the acceleration for the new block
	 */
	private BigInteger computeAcceleration(long weightedWaitingTimeForNewBlock, long targetBlockCreationTime) {
		var oldAcceleration = getAcceleration();
		var delta = oldAcceleration
			.multiply(BigInteger.valueOf(weightedWaitingTimeForNewBlock))
			.divide(BigInteger.valueOf(targetBlockCreationTime))
			.subtract(oldAcceleration);
	
		var acceleration = oldAcceleration.add(delta.multiply(_20).divide(_100));
		if (acceleration.signum() == 0)
			acceleration = BigInteger.ONE; // acceleration must be strictly positive
	
		return acceleration;
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