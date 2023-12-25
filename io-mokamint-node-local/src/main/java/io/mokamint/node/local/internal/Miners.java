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

import java.util.Optional;
import java.util.stream.Stream;

import io.hotmoka.annotations.ThreadSafe;
import io.mokamint.miner.api.Miner;
import io.mokamint.node.MinerInfos;
import io.mokamint.node.api.MinerInfo;
import io.mokamint.node.local.api.LocalNodeConfig;

/**
 * The set of miners of a local node.
 */
@ThreadSafe
public class Miners {

	/**
	 * The node having these miners.
	 */
	private final LocalNodeImpl node;

	/**
	 * The miners of the node.
	 */
	private final PunishableSet<Miner> miners;

	/**
	 * The configuration of the node having these miners.
	 */
	private final LocalNodeConfig config;

	/**
	 * Creates a container for the miners of a local node.
	 * 
	 * @param node the node
	 */
	public Miners(LocalNodeImpl node) {
		this.node = node;
		this.config = node.getConfig();
		this.miners = new PunishableSet<>(Stream.empty(), config.getMinerInitialPoints());
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
	 * Yields information about the miners.
	 * 
	 * @return information about the miners
	 */
	public Stream<MinerInfo> getInfos() {
		return miners.getActorsWithPoints().map(entry -> MinerInfos.of(entry.getKey().getUUID(), entry.getValue(), entry.getKey().toString()));
	}

	/**
	 * Adds the given miner to this container, if it was not already there.
	 * 
	 * @param miner the miner to add
	 * @return the information about the added miner; this is empty if the miner has not been added
	 */
	public Optional<MinerInfo> add(Miner miner) {
		if (miners.add(miner)) {
			node.onAdded(miner);
			return Optional.of(MinerInfos.of(miner.getUUID(), config.getMinerInitialPoints(), miner.toString()));
		}
		else
			return Optional.empty();
	}

	/**
	 * Removes the given miner from this container, if it was there.
	 * 
	 * @param miner the miner to remove
	 * @return true if and only if the miner has been removed
	 */
	public boolean remove(Miner miner) {
		if (miners.remove(miner)) {
			node.onRemoved(miner);
			return true;
		}
		else
			return false;
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
		if (miners.punish(miner, points)) {
			node.onRemoved(miner);
			return true;
		}
		else
			return false;
	}

	/**
	 * Pardons a miner, by increasing its points. There is a maximal
	 * value to the resulting points. If the miner was not present in this
	 * container, nothing happens.
	 * 
	 * @param miner the miner to pardon
	 * @param points how many points get pardoned
	 * @throws IllegalArgumentException if {@code points} is negative
	 */
	public void pardon(Miner miner, long points) {
		miners.pardon(miner, points);
	}
}