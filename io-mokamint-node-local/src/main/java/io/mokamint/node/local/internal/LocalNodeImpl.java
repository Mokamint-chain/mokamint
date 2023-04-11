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

		while (true)
			new NextBlockCreation();
	}

	private class NextBlockCreation {

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

		private NextBlockCreation() {
			var random = new Random();
			this.scoopNumber = random.nextInt(Nonce.SCOOPS_PER_NONCE);
			random.nextBytes(data);

			LOGGER.info("starting new block for scoop number: " + scoopNumber + " and data: " + Hex.toHexString(data));

			requestDeadlineToEveryMiner();
			waitUntilDeadlineExpiresOrBlockArrivesFromPeers();
			
			LOGGER.info("finished new block for scoop number: " + scoopNumber + " and data: " + Hex.toHexString(data));
		}

		private void waitUntilDeadlineExpiresOrBlockArrivesFromPeers() {
			try {
				Thread.sleep(10000);
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
					if (updateCurrent(deadline))
						LOGGER.info("improved deadline " + deadline);
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