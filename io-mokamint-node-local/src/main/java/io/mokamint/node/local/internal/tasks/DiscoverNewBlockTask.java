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

public class DiscoverNewBlockTask extends Task {
	private final static Logger LOGGER = Logger.getLogger(DiscoverNewBlockTask.class.getName());
	private final static BigInteger SCOOPS_PER_NONCE = BigInteger.valueOf(Nonce.SCOOPS_PER_NONCE);
	private final static BigInteger _100 = BigInteger.valueOf(100L);
	private final static BigInteger _20 = BigInteger.valueOf(20L);

	/**
	 * The target block creation time that will be aimed, in milliseconds.
	 */
	private final static BigInteger TARGET_BLOCK_CREATION_TIME = BigInteger.valueOf(5 * 1000); // 5 seconds

	private final Block previous;

	public DiscoverNewBlockTask(LocalNodeImpl node, Block previous) {
		node.super();

		this.previous = previous;
	}

	@Override
	public void run() {
		new Run();
	}

	private class Run {

		/**
		 * The scoop number of the deadline for the creation of next block.
		 */
		private final int scoopNumber;

		/**
		 * The generation signature for the creation of the next block.
		 */
		private final byte[] generationSignature;

		private final HashingAlgorithm<byte[]> sha256;

		/**
		 * The best deadline computed so far. This might be {@code null}.
		 */
		private volatile Deadline bestDeadline;

		private Run() {
			System.out.println("\nBlock " + previous.getNumber());

			try {
				this.sha256 = HashingAlgorithms.sha256((byte[] bytes) -> bytes);
			}
			catch (NoSuchAlgorithmException e) {
				throw new RuntimeException(e);
			}

			if (previous instanceof NonGenesisBlock) {
				Deadline previousDeadline = ((NonGenesisBlock) previous).getDeadline();
				byte[] previousGenerationSignature = previousDeadline.getData();
				byte[] previousProlog = previousDeadline.getProlog();
				byte[] merge = concat(previousGenerationSignature, previousProlog);
				this.generationSignature = sha256.hash(merge);
			}
			else
				this.generationSignature = new byte[] { 13, 1, 19, 73 };

			byte[] blockHeight = longToBytesBE(Long.valueOf(previous.getNumber() + 1));
			byte[] generationHash = sha256.hash(concat(generationSignature, blockHeight));

			this.scoopNumber = new BigInteger(1, generationHash).remainder(SCOOPS_PER_NONCE).intValue();

			LOGGER.info("starting working at new block for scoop number: " + scoopNumber + " and generationSignature: " + Hex.toHexString(generationSignature));

			requestDeadlineToEveryMiner();
			waitUntilDeadlineExpires();
			Block block = createNewBlock();

			LOGGER.info("finished new block for scoop number: " + scoopNumber + " and generationSignature: " + Hex.toHexString(generationSignature));

			getNode().signal(getNode().new BlockDiscoveredEvent(block));
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
			getNode().forEachMiner(miner -> miner.requestDeadline(scoopNumber, generationSignature, this::onDeadlineComputed));
		}

		/**
		 * This call-back might be executed on a different thread.
		 * 
		 * @param deadline the deadline that has just been computed by some of the miners of this node
		 */
		private void onDeadlineComputed(Deadline deadline, Miner miner) {
			LOGGER.info("received deadline " + deadline);

			if (!getNode().hasMiner(miner))
				LOGGER.info("discarding deadline " + deadline + " since its miner is unknown");
			else if (improvesCurrent(deadline)) {
				if (isLegal(deadline)) {
					if (updateCurrent(deadline)) {
						LOGGER.info("improved deadline " + deadline);
						wakeUpBlockCreatorOrSetWaker(deadline);
					}
					else
						LOGGER.info("discarding deadline " + deadline + " since it's larger than the current deadline");
				}
				else {
					LOGGER.info("discarding deadline " + deadline + " since it's illegal");
					getNode().signal(getNode().new MinerMisbehaviorEvent(miner));
				}
			}
			else
				LOGGER.info("discarding deadline " + deadline + " since it's larger than the current deadline");
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
			var waitingTime = millisecondsToWaitFor();
			System.out.println("I wait " + asSeconds(waitingTime) + " seconds");

			BigInteger weightedWaitingTime = (previous.getWeightedWaitingTime().multiply(BigInteger.valueOf(95))
					.add(waitingTime.multiply(BigInteger.valueOf(5))))
					.divide(_100);

			long blockNumber = previous.getNumber() + 1;

			BigInteger totalWaitingTime = previous.getTotalWaitingTime().add(waitingTime);

			System.out.println("average waiting time is " + asSeconds(totalWaitingTime.divide(BigInteger.valueOf(blockNumber))) + " seconds");

			// update the acceleration in order to get closer to the target creation time
			BigInteger oldAcceleration = previous.getAcceleration();
			BigInteger delta = oldAcceleration
					.multiply(weightedWaitingTime)
					.divide(TARGET_BLOCK_CREATION_TIME)
					.subtract(oldAcceleration);

			BigInteger acceleration = oldAcceleration.add(delta.multiply(_20).divide(_100));
			if (acceleration.signum() == 0)
				acceleration = BigInteger.ONE;

			return Block.of(blockNumber, totalWaitingTime, weightedWaitingTime, acceleration, bestDeadline);
		}

		private String asSeconds(BigInteger milliseconds) {
			BigInteger[] inSeconds = milliseconds.divideAndRemainder(BigInteger.valueOf(1000L));
			return inSeconds[0] + "." + (inSeconds[1].intValue() / 10);
		}

		private BigInteger millisecondsToWaitFor() {
			byte[] valueAsBytes = bestDeadline.getValue();
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
					dividedValueAsBytes[0],
					dividedValueAsBytes[1],
					dividedValueAsBytes[2],
					dividedValueAsBytes[3],
					dividedValueAsBytes[4],
					dividedValueAsBytes[5],
					dividedValueAsBytes[6],
					dividedValueAsBytes[7]
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
		 * Determines if a deadline can be accepted, that is,
		 * it is actually a deadline for this scoop number and generationSignature, it is valid
		 * and its prolog is valid for the application. All these conditions should always
		 * hold, if the miner behaves correctly.
		 * 
		 * @param deadline the deadline to check
		 * @return true if and only if the deadline can be accepted
		 */
		private boolean isLegal(Deadline deadline) {
			return deadline.getScoopNumber() == scoopNumber
					&& Arrays.equals(deadline.getData(), generationSignature)
					&& deadline.isValid()
					&& getNode().getApplication().prologIsValid(deadline.getProlog());
		}

		/**
		 * Determines if the given deadline is better than the current best.
		 * 
		 * @param deadline the deadline
		 * @return true if and only if it is better than the current best
		 */
		private boolean improvesCurrent(Deadline deadline) {
			// it's OK not to synchronize, the risk is only to consider a spurious deadline
			// that will be blocked by {@code improvesBest()} later
			return bestDeadline == null || deadline.compareByValue(bestDeadline) < 0;
		}

		/**
		 * Updates the current best deadline if the given deadline is smaller.
		 * 
		 * @param deadline the deadline
		 * @return true if and only if the current best deadline has been updated
		 */
		private synchronized boolean updateCurrent(Deadline deadline) {
			if (improvesCurrent(deadline)) {
				bestDeadline = deadline;
				return true;
			}
			else
				return false;
		}
	}
}