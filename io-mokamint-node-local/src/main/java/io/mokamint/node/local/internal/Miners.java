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

import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import io.hotmoka.annotations.ThreadSafe;
import io.mokamint.miner.api.Miner;
import io.mokamint.node.local.Config;

/**
 * A set of miners.
 */
@ThreadSafe
public class Miners {

	/**
	 * The configuration of the node having this set of miners.
	 */
	private final Config config;

	/**
	 * The container of the miners. Each miner is mapped to its points.
	 * When a miner misbehaves, its points are reduced, until they reach 0 and the
	 * miner is discarded.
	 */
	private final Map<Miner, Long> miners;
	/**
	 * Creates a new set of miners.
	 * 
	 * @param config the configuration of the node having this set of miners
	 * @param miners the miners contained in the set
	 */
	public Miners(Config config, Stream<Miner> miners) {
		this.config = config;

		long minerInitialPoints = config.minerInitialPoints;
		this.miners = miners.collect(Collectors.toMap
			(miner -> miner, miner -> minerInitialPoints, (i1, i2) -> minerInitialPoints, HashMap::new));
	}

	/**
	 * Checks if the given miner is among those of this container.
	 * 
	 * @param miner the miner
	 * @return true if and only if that condition holds
	 */
	public boolean contains(Miner miner) {
		synchronized (miners) {
			return miners.containsKey(miner);
		}
	}

	/**
	 * Checks is the container is empty.
	 * 
	 * @return true if and only if the container is empty
	 */
	public boolean isEmpty() {
		synchronized (miners) {
			return miners.isEmpty();
		}
	}

	/**
	 * Punishes a miner, by reducing its points. If the miner reaches 0 points,
	 * it gets removed from this set of miners.
	 * 
	 * @param miner the miner to punish
	 * @param points how many points get removed
	 */
	void punish(Miner miner, long points) {
		synchronized (miners) {
			if (miners.computeIfPresent(miner, (__, oldPoints) -> Math.max(0L, oldPoints - points)) == 0)
				miners.remove(miner);
		}
	}

	/**
	 * Adds the given miner from this container, if it is not already there.
	 * Otherwise, nothing is added.
	 * 
	 * @param miner the miner to add
	 */
	void add(Miner miner) {
		synchronized (miners) {
			miners.put(miner, config.minerInitialPoints);
		}
	}

	/**
	 * Runs some code on each miner. This is weakly consistent,
	 * in the sense that the set of miners can be modified in the meanwhile and there is
	 * no guarantee that the code will be run for such newly added miners.
	 * 
	 * @param what the code to execute for each miner
	 */
	public void forEach(Consumer<Miner> what) {
		Map<Miner, Long> copy;

		synchronized (miners) {
			copy = new HashMap<>(miners);
		}

		copy.forEach((miner, _points) -> what.accept(miner));
	}
}
