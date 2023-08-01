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

import java.util.stream.Stream;

import io.hotmoka.annotations.ThreadSafe;
import io.mokamint.miner.api.Miner;

/**
 * The set of miners of a local node.
 */
@ThreadSafe
public class NodeMiners {

	/**
	 * The miners of the node.
	 */
	private final PunishableSet<Miner> miners;

	/**
	 * Creates a container for the miners of a local node.
	 * 
	 * @param node the node
	 * @param the miners that go into this container
	 */
	public NodeMiners(LocalNodeImpl node, Stream<Miner> miners) {
		this.miners = PunishableSets.of(miners, node.getConfig().minerInitialPoints);
	}

	/**
	 * Yields the miners.
	 * 
	 * @return the miners
	 */
	public Stream<Miner> get() {
		return miners.getElements();
	}

	/**
	 * Punishes a miner, by reducing its points. If the miner reaches zero points,
	 * it gets removed from this container. If the miner was not present in this
	 * container, nothing happens.
	 * 
	 * @param miner the miner to punish
	 * @param points how many points get removed
	 * @return true if and only if the miner was present in this container,
	 *         has reached zero points and has been removed
	 */
	public boolean punish(Miner miner, long points) {
		return miners.punish(miner, points);
	}
}