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

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import io.hotmoka.crypto.Hex;
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

	private final ExecutorService executors;

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
		this.executors = Executors.newFixedThreadPool(miners.length + 1);

		for (var miner: miners) {
			miner.requestDeadline(13, new byte[] { 18, 34, 80, 11 }, this::onDeadlineComputed);
		}
	}

	private void onDeadlineComputed(Deadline deadline) {
		System.out.println("received deadline " + Hex.toHexString(deadline.getValue()));
	}

	@Override
	public void close() {
		executors.shutdown();
	}
}