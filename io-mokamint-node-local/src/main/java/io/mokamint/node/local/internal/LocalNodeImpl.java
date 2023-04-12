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

package io.mokamint.node.local.internal;

import java.math.BigInteger;
import java.util.Arrays;
import java.util.Random;
import java.util.Set;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import io.hotmoka.crypto.Hex;
import io.mokamint.application.api.Application;
import io.mokamint.miner.api.Miner;
import io.mokamint.node.api.Node;
import io.mokamint.nonce.api.Deadline;
import io.mokamint.nonce.api.Nonce;

/**
 * A local node of a Mokamint blockchain.
 */
public class LocalNodeImpl implements Node {

	/**
	 * The application executed by the blockchain.
	 */
	private final Application app;

	/**
	 * The miners connected to the node.
	 */
	private final Set<Miner> miners;

	/**
	 * The target block creation time that will be aimed, in milliseconds.
	 */
	private final static BigInteger targetBlockCreationTime = BigInteger.valueOf(4L * 60 * 1000); // 4 minutes

	private BigInteger weightedWaitingTime = targetBlockCreationTime;
	
	private BigInteger totalWaitingTime = BigInteger.ZERO;

	private long blockNumber = 0L;

	/**
	 * A value used to divide the deadline to derive the time needed to wait for them.
	 * The higher, the shorter the time. This value changes dynamically to cope with
	 * varying mining power in the network. It is the inverse of Bitcoin's difficulty.
	 */
	private BigInteger acceleration = BigInteger.valueOf(100000000000L);

	private final static Logger LOGGER = Logger.getLogger(LocalNodeImpl.class.getName());

	/**
	 * Creates a local node of a Mokamint blockchain, for the given application,
	 * using the given miners.
	 *
	 * @param app the application
	 * @param miners the miners
	 */
	public LocalNodeImpl(Application app, Miner... miners) {
		this.app = app;
		this.miners = Stream.of(miners).collect(Collectors.toSet());

		while (true) {
			var creator = new NextBlockCreator();
			var waitingTime = millisecondsToWaitFor(creator.bestDeadline);
			System.out.println("I wait " + asSeconds(waitingTime) + " seconds");

			weightedWaitingTime = (weightedWaitingTime.multiply(BigInteger.valueOf(98)).add(waitingTime.multiply(BigInteger.valueOf(2))))
				.divide(BigInteger.valueOf(100));

			blockNumber++;

			totalWaitingTime = totalWaitingTime.add(waitingTime);

			System.out.println("average waiting time is " + asSeconds(totalWaitingTime.divide(BigInteger.valueOf(blockNumber))) + " seconds");

			// update the acceleration in order to get closer to the target creation time
			BigInteger delta = acceleration.multiply(weightedWaitingTime).divide(targetBlockCreationTime).subtract(acceleration);
			acceleration = acceleration.add(delta.multiply(BigInteger.valueOf(20L)).divide(BigInteger.valueOf(100L)));
			if (acceleration.signum() == 0)
				acceleration = BigInteger.ONE;
		}
	}

	private BigInteger millisecondsToWaitFor(Deadline deadline) {
		byte[] valueAsBytes = deadline.getValue();
		BigInteger value = new BigInteger(1, valueAsBytes);
		value = value.divide(acceleration);
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

	private String asSeconds(BigInteger milliseconds) {
		BigInteger[] inSeconds = milliseconds.divideAndRemainder(BigInteger.valueOf(1000L));
		return inSeconds[0] + "." + (inSeconds[1].intValue() / 10);
	}

	private class NextBlockCreator {

		/**
		 * The scoop number of the deadline for the creation of next block.
		 */
		private final int scoopNumber;

		/**
		 * The data of the deadline for the creation of the next block.
		 */
		private final byte[] data = new byte[14];

		/**
		 * The best deadline computed so far. This might be {@code null}.
		 */
		private volatile Deadline bestDeadline;

		private NextBlockCreator() {
			var random = new Random();
			this.scoopNumber = random.nextInt(Nonce.SCOOPS_PER_NONCE);
			random.nextBytes(data);
			
			System.out.println("\nBlock " + blockNumber + ", acceleration is now " + acceleration);

			LOGGER.info("starting new block for scoop number: " + scoopNumber + " and data: " + Hex.toHexString(data));

			requestDeadlineToEveryMiner();
			waitUntilDeadlineExpiresOrBlockArrivesFromPeers();

			LOGGER.info("finished new block for scoop number: " + scoopNumber + " and data: " + Hex.toHexString(data));
		}

		private void waitUntilDeadlineExpiresOrBlockArrivesFromPeers() {
			try {
				Thread.sleep(600);
			}
			catch (InterruptedException e) {
				e.printStackTrace();
			}
		}

		private void requestDeadlineToEveryMiner() {
			for (var miner: miners)
				miner.requestDeadline(scoopNumber, data, this::onDeadlineComputed);
		}

		/**
		 * This call-back might be executed on a different thread.
		 * 
		 * @param deadline the deadline that has just been computed by some of the miners of this node
		 */
		private void onDeadlineComputed(Deadline deadline, Miner miner) {
			LOGGER.info("received deadline " + deadline);

			if (!miners.contains(miner))
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
					punish(miner);
					LOGGER.info("discarding deadline " + deadline + " since it's illegal and punishing its miner");
				}
			}
			else
				LOGGER.info("discarding deadline " + deadline + " since it's larger than the current deadline");
		}

		/**
		 * @param deadline
		 */
		private void wakeUpBlockCreatorOrSetWaker(Deadline deadline) {
			BigInteger millisecondsToWait = millisecondsToWaitFor(deadline);
			//System.out.println("Should wait " + asSeconds(millisecondsToWait) + " seconds");
		}

		/**
		 * Punishes a miner that misbehaved.
		 * 
		 * @param miner the miner
		 */
		private void punish(Miner miner) {
			miners.remove(miner); // TODO
		}

		/**
		 * Determines if a deadline can be accepted, that is,
		 * it is actually a deadline for this scoop number and data, it is valid
		 * and its prolog is valid for the application. All these conditions should always
		 * hold, if the miner behaves correctly.
		 * 
		 * @param deadline the deadline to check
		 * @return true if and only if the deadline can be accepted
		 */
		private boolean isLegal(Deadline deadline) {
			return deadline.getScoopNumber() == scoopNumber
					&& Arrays.equals(deadline.getData(), data)
					&& deadline.isValid()
					&& app.prologIsValid(deadline.getProlog());
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

	@Override
	public void close() {
	}
}