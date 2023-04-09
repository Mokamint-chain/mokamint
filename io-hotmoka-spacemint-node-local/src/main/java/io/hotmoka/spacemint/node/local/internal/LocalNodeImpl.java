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

package io.hotmoka.spacemint.node.local.internal;

import java.util.Arrays;
import java.util.logging.Logger;

import io.hotmoka.spacemint.application.api.Application;
import io.hotmoka.spacemint.miner.api.Miner;
import io.hotmoka.spacemint.node.api.Node;
import io.hotmoka.spacemint.nonce.api.Deadline;

/**
 * A local node of a Spacemint blockchain.
 */
public class LocalNodeImpl implements Node {

	/**
	 * The application executed by the blockchain.
	 */
	private final Application app;

	/**
	 * The miners connected to the node.
	 */
	private final Miner[] miners;

	/**
	 * The scoop number of the deadline this node is waiting.
	 */
	private volatile int scoopNumber;

	/**
	 * The data of the deadline this node is waiting.
	 */
	private volatile byte[] data;

	/**
	 * The best deadline computed so far. This might be {@code null}.
	 */
	private volatile Deadline bestDeadline;
	
	private final static Logger LOGGER = Logger.getLogger(LocalNodeImpl.class.getName());

	/**
	 * Creates a local node of a Spacemint blockchain, for the given application,
	 * using the given miners.
	 *
	 * @param app the application
	 * @param miners the miners
	 */
	public LocalNodeImpl(Application app, Miner... miners) {
		this.app = app;
		this.miners = miners;

		this.scoopNumber = 13;
		this.data = new byte[] { 18, 34, 80, 11 };
		this.bestDeadline = null;

		waitForDeadline();
	}

	private void waitForDeadline() {
		for (var miner: miners) {
			miner.requestDeadline(scoopNumber, data, this::onDeadlineComputed);
		}
	}

	/**
	 * This call-back might be executed on a different thread.
	 * 
	 * @param deadline the deadline that has just been computed by some of the miners of this node
	 */
	private void onDeadlineComputed(Deadline deadline) {
		LOGGER.info("received deadline " + deadline);
		if (deadline.getScoopNumber() == scoopNumber
				&& Arrays.equals(deadline.getData(), data)
				&& app.prologIsValid(deadline.getProlog())) {

			LOGGER.info("accepted deadline " + deadline);
			if (bestDeadline == null || deadline.compareByValue(bestDeadline) < 0) {
				bestDeadline = deadline;
				LOGGER.info("improved deadline " + bestDeadline);
			}
		}
		else
			LOGGER.info("discarding deadline " + deadline);
	}

	@Override
	public void close() {
	}
}