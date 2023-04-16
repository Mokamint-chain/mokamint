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

package io.mokamint.node.local.internal.tasks;

import java.math.BigInteger;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.logging.Logger;

import io.hotmoka.annotations.OnThread;
import io.hotmoka.crypto.HashingAlgorithms;
import io.hotmoka.crypto.Hex;
import io.hotmoka.crypto.api.HashingAlgorithm;
import io.mokamint.miner.api.Miner;
import io.mokamint.node.local.internal.LocalNodeImpl;
import io.mokamint.node.local.internal.LocalNodeImpl.Task;
import io.mokamint.node.local.internal.blockchain.Block;
import io.mokamint.node.local.internal.blockchain.NonGenesisBlock;
import io.mokamint.nonce.api.Deadline;
import io.mokamint.nonce.api.Nonce;

/**
 * A task that mines a new block, follower of a previous block.
 * It requests a deadline to the miners of the node and waits for the best deadline to expire.
 * Once expired, it builds the block and signals a new block discovery to the node.
 */
public class MineNewBlockTask extends Task {
	private final static Logger LOGGER = Logger.getLogger(MineNewBlockTask.class.getName());
	private final static BigInteger SCOOPS_PER_NONCE = BigInteger.valueOf(Nonce.SCOOPS_PER_NONCE);
	private final static BigInteger _5 = BigInteger.valueOf(5L);
	private final static BigInteger _20 = BigInteger.valueOf(20L);
	private final static BigInteger _95 = BigInteger.valueOf(95L);
	private final static BigInteger _100 = BigInteger.valueOf(100L);

	/**
	 * The target block creation time that will be aimed, in milliseconds.
	 */
	private final static BigInteger TARGET_BLOCK_CREATION_TIME = BigInteger.valueOf(5 * 1000); // 5 seconds

	/**
	 * The generation signature for the block on top of the genesis block. This is arbitrary.
	 */
	private final static byte[] BLOCK_1_GENERATION_SIGNATURE = new byte[] { 13, 1, 19, 73 };

	/**
	 * The block for which a subsequent block is being mined.
	 */
	private final Block previous;

	/**
	 * Creates a task that mines a new block.
	 * 
	 * @param node the node for which this task is working
	 * @param previous the node for which a subsequent node is being built
	 */
	public MineNewBlockTask(LocalNodeImpl node, Block previous) {
		node.super();

		this.previous = previous;
	}

	@Override
	public void run() {
		try {
			new Run();
		}
		catch (NoSuchAlgorithmException e) {
			throw new RuntimeException(e);
		}
	}

	/**
	 * Run environment.
	 */
	private class Run {

		/**
		 * The hashing algorithm used to compute the next generation signature and scoop number.
		 */
		private final HashingAlgorithm<byte[]> sha256;

		/**
		 * The height of the new block that is being mined.
		 */
		private final long heightOfNewBlock = previous.getHeight() + 1;

		/**
		 * The scoop number of the deadline for the creation of the next block.
		 */
		private final int scoopNumber;

		/**
		 * The generation signature for the creation of the next block.
		 */
		private final byte[] generationSignature;

		/**
		 * The best deadline computed so far. This is empty until a first deadline is found. Since more miners
		 * might work for a node, this best deadline might be set more than once, to increasingly better deadlines.
		 */
		private final ImprovableDeadline bestDeadline = new ImprovableDeadline();

		/**
		 * The new block that will be mined at the end.
		 */
		private final Block block;

		private Run() throws NoSuchAlgorithmException {
			LOGGER.info("starting mining new block at height " + heightOfNewBlock);
			this.sha256 = HashingAlgorithms.sha256((byte[] bytes) -> bytes);
			this.generationSignature = computeGenerationSignature();
			this.scoopNumber = computeScoopNumber();
			requestDeadlineToEveryMiner();
			waitUntilDeadlineExpires();
			this.block = createNewBlock();
			informNodeAboutNewBlock();
		}

		private byte[] computeGenerationSignature() {
			if (previous instanceof NonGenesisBlock) {
				Deadline previousDeadline = ((NonGenesisBlock) previous).getDeadline();
				byte[] previousGenerationSignature = previousDeadline.getData();
				byte[] previousProlog = previousDeadline.getProlog();
				byte[] merge = concat(previousGenerationSignature, previousProlog);
				return sha256.hash(merge);
			}
			else
				return BLOCK_1_GENERATION_SIGNATURE;
		}

		private int computeScoopNumber() {
			byte[] generationHash = sha256.hash(concat(generationSignature, longToBytesBE(heightOfNewBlock)));
			return new BigInteger(1, generationHash).remainder(SCOOPS_PER_NONCE).intValue();
		}

		private void informNodeAboutNewBlock() {
			LOGGER.info("ended mining new block at height " + heightOfNewBlock + ": informing the node");
			getNode().signal(getNode().new BlockDiscoveryEvent(block));
		}

		private byte[] concat(byte[] array1, byte[] array2) {
			byte[] merge = new byte[array1.length + array2.length];
			System.arraycopy(array1, 0, merge, 0, array1.length);
			System.arraycopy(array2, 0, merge, array1.length, array2.length);
			return merge;
		}

		private byte[] longToBytesBE(long l) {
			byte[] target = new byte[8];

			for (int i = 0; i <= 7; i++)
				target[7 - i] = (byte) ((l>>(8*i)) & 0xFF);

			return target;
		}

		private void requestDeadlineToEveryMiner() {
			LOGGER.info("for new block at height " + heightOfNewBlock +
					", asking to the miners a deadline with scoop number: " + scoopNumber +
					" and data: " + Hex.toHexString(generationSignature));
			getNode().forEachMiner(miner -> miner.requestDeadline(scoopNumber, generationSignature, this::onDeadlineComputed));
		}

		/**
		 * Called by miners when they find a deadline.
		 * 
		 * @param deadline the deadline that has just been computed
		 * @param miner the miner that found the deadline
		 */
		@OnThread("miner")
		private void onDeadlineComputed(Deadline deadline, Miner miner) {
			LOGGER.info("received deadline " + deadline);

			if (!getNode().hasMiner(miner))
				LOGGER.info("discarding deadline " + deadline + " since its miner is unknown");
			else if (bestDeadline.isWorseThan(deadline)) {
				if (isLegal(deadline)) {
					if (bestDeadline.updateIfWorseThan(deadline)) {
						LOGGER.info("improved deadline to " + deadline);
						wakeUpBlockCreatorOrSetWaker(deadline);
					}
					else
						LOGGER.info("discarding deadline " + deadline + " since it's worse than the current deadline");
				}
				else {
					LOGGER.info("discarding deadline " + deadline + " since it's illegal");
					getNode().signal(getNode().new MinerMisbehaviorEvent(miner));
				}
			}
			else
				LOGGER.info("discarding deadline " + deadline + " since it's worse than the current deadline");
		}

		private void waitUntilDeadlineExpires() {
			try {
				Thread.sleep(600);
			}
			catch (InterruptedException e) {
				e.printStackTrace();
			}
		}

		private Block createNewBlock() {
			var deadline = bestDeadline.get().get(); // here, we know that a deadline has been computed
			var waitingTimeForNewBlock = millisecondsToWaitFor(deadline);
			System.out.print("I wait " + asSeconds(waitingTimeForNewBlock) + " seconds. ");
			var weightedWaitingTimeForNewBlock = computeWeightedWaitingTime(waitingTimeForNewBlock);
			var totalWaitingTimeForNewBlock = computeTotalWaitingTime(waitingTimeForNewBlock);
			System.out.println("Average waiting time is " + asSeconds(totalWaitingTimeForNewBlock.divide(BigInteger.valueOf(heightOfNewBlock))) + " seconds");
			var accelerationForNewBlock = computeAcceleration(weightedWaitingTimeForNewBlock);

			return Block.of(heightOfNewBlock, totalWaitingTimeForNewBlock, weightedWaitingTimeForNewBlock, accelerationForNewBlock, deadline);
		}

		/**
		 * Computes the acceleration for the new block, in order to get closer to the target creation time.
		 * 
		 * @param weightedWaitingTimeForNewBlock the weighted waiting time for the new block
		 * @return the acceleration for the new block
		 */
		private BigInteger computeAcceleration(BigInteger weightedWaitingTimeForNewBlock) {
			BigInteger oldAcceleration = previous.getAcceleration();
			BigInteger delta = oldAcceleration
					.multiply(weightedWaitingTimeForNewBlock)
					.divide(TARGET_BLOCK_CREATION_TIME)
					.subtract(oldAcceleration);

			BigInteger acceleration = oldAcceleration.add(delta.multiply(_20).divide(_100));
			if (acceleration.signum() == 0)
				acceleration = BigInteger.ONE; // acceleration must be strictly positive

			return acceleration;
		}

		private BigInteger computeTotalWaitingTime(BigInteger waitingTime) {
			return previous.getTotalWaitingTime().add(waitingTime);
		}

		private BigInteger computeWeightedWaitingTime(BigInteger waitingTime) {
			BigInteger previousWeightedWaitingTime_95 = previous.getWeightedWaitingTime().multiply(_95);
			BigInteger waitingTime_5 = waitingTime.multiply(_5);
			BigInteger weightedWaitingTime = previousWeightedWaitingTime_95.add(waitingTime_5).divide(_100);
			return weightedWaitingTime;
		}

		private String asSeconds(BigInteger milliseconds) {
			BigInteger[] inSeconds = milliseconds.divideAndRemainder(BigInteger.valueOf(1000L));
			return inSeconds[0] + "." + (inSeconds[1].intValue() / 10);
		}

		private BigInteger millisecondsToWaitFor(Deadline deadline) {
			byte[] valueAsBytes = deadline.getValue();
			BigInteger value = new BigInteger(1, valueAsBytes);
			value = value.divide(previous.getAcceleration());
			byte[] newValueAsBytes = value.toByteArray();
			// we recreate an array of the same length as at the beginning
			byte[] dividedValueAsBytes = new byte[valueAsBytes.length];
			System.arraycopy(newValueAsBytes, 0, dividedValueAsBytes,
					dividedValueAsBytes.length - newValueAsBytes.length,
					newValueAsBytes.length);
			// we take the first 8 bytes of the divided value
			byte[] firstEightBytes = new byte[] {
					dividedValueAsBytes[0], dividedValueAsBytes[1], dividedValueAsBytes[2], dividedValueAsBytes[3],
					dividedValueAsBytes[4], dividedValueAsBytes[5], dividedValueAsBytes[6], dividedValueAsBytes[7]
			};
			return new BigInteger(1, firstEightBytes);
		}

		/**
		 * @param deadline
		 */
		private void wakeUpBlockCreatorOrSetWaker(Deadline deadline) {
			//BigInteger millisecondsToWait = millisecondsToWaitFor(deadline);
			//System.out.println("Should wait " + asSeconds(millisecondsToWait) + " seconds");
		}

		/**
		 * Determines if a deadline is legal, that is,
		 * it is actually a deadline for the expected scoop number and generationSignature,
		 * it is valid and its prolog is valid for the application. All these conditions
		 * should always hold, if the miner behaves correctly.
		 * 
		 * @param deadline the deadline to check
		 * @return true if and only if the deadline is legal
		 */
		private boolean isLegal(Deadline deadline) {
			return deadline.getScoopNumber() == scoopNumber
					&& Arrays.equals(deadline.getData(), generationSignature)
					&& deadline.isValid()
					&& getNode().getApplication().prologIsValid(deadline.getProlog());
		}
	}
}