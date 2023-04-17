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

import java.util.HashSet;
import java.util.Set;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import io.hotmoka.annotations.GuardedBy;
import io.hotmoka.annotations.ThreadSafe;
import io.mokamint.miner.api.Miner;

/**
 * A set of miners.
 */
@ThreadSafe
public class Miners {

	/**
	 * The containers of the miners.
	 */
	@GuardedBy("itself")
	private final Set<Miner> miners;

	/**
	 * 
	 */
	public Miners(Stream<Miner> miners) {
		this.miners = miners.collect(Collectors.toCollection(HashSet::new));
	}

	/**
	 * Checks if the given miner is among those of this container.
	 * 
	 * @param miner the miner
	 * @return true if and only if that condition holds
	 */
	public boolean contains(Miner miner) {
		synchronized (miners) {
			return miners.contains(miner);
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
	 * Removes the given miner from this container, if it is in this container.
	 * Otherwise, nothing is removed.
	 * 
	 * @param miner the miner to remove
	 */
	void remove(Miner miner) {
		synchronized (miners) {
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
			miners.add(miner);
		}
	}

	/**
	 * Yields the miners in this container. This is weakly consistent,
	 * in the sense that the set of miners can be modified in the meanwhile and there is
	 * no guarantee that the code will be run for any newly added miner.
	 * 
	 * @return the miners
	 */
	public Stream<Miner> stream() {
		Set<Miner> copy;
		synchronized (miners) {
			copy = new HashSet<>(miners);
		}

		return copy.stream();
	}

	/**
	 * Runs some code on each miner connected to this node. This is weakly consistent,
	 * in the sense that the set of miners can be modified in the meanwhile and there is
	 * no guarantee that the code will be run for such newly added miners.
	 * 
	 * @param what the code to execute for each miner
	 */
	public void forEachMiner(Consumer<Miner> what) {
		// it's OK to be weakly consistent
		Set<Miner> copy;
		synchronized (miners) {
			copy = new HashSet<>(miners);
		}

		copy.forEach(what);
	}
}
