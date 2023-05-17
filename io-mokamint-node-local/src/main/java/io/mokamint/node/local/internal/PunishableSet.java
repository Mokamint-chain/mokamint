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

import static java.util.stream.Collectors.toMap;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;
import java.util.stream.Stream;

import io.hotmoka.annotations.ThreadSafe;

/**
 * A set of actors that can be punished and potentially removed from the set
 * if they have been too much punished.
 * 
 * @param <A> the type of the actors
 */
@ThreadSafe
public class PunishableSet<A> {

	/**
	 * The container of the actors. Each actor is mapped to its points. When an actor
	 * gets punished, its points are reduced, until they reach zero and the actor is discarded.
	 */
	private final Map<A, Long> actors;

	/**
	 * The initial points assigned to a new actor.
	 */
	private final long initialPoints;

	/**
	 * Creates a new punishable set of actors.
	 * 
	 * @param actors the actors initially contained in the set
	 * @param initialPoints the initial points assigned to an actor when it is added to the set
	 */
	public PunishableSet(Stream<A> actors, long initialPoints) {
		this.initialPoints = initialPoints;
		this.actors = actors.collect(toMap(actor -> actor, _actor -> initialPoints, (_i1, _i2) -> initialPoints, HashMap::new));
	}

	/**
	 * Checks if the given actor is among those of this container.
	 * 
	 * @param actor the actor
	 * @return true if and only if that condition holds
	 */
	public boolean contains(A actor) {
		synchronized (actors) {
			return actors.containsKey(actor);
		}
	}

	/**
	 * Checks is this set is empty.
	 * 
	 * @return true if and only if this set is empty
	 */
	public boolean isEmpty() {
		synchronized (actors) {
			return actors.isEmpty();
		}
	}

	/**
	 * Runs some code on each actor in this set. This is weakly consistent,
	 * in the sense that the set of actors can be modified in the meantime and there is
	 * no guarantee that the code will be run for such newly added actors.
	 * 
	 * @param what the code to execute for each actor
	 */
	public void forEach(Consumer<A> what) {
		Map<A, Long> copy;
	
		synchronized (actors) {
			copy = new HashMap<>(actors);
		}
	
		copy.forEach((miner, _points) -> what.accept(miner));
	}

	/**
	 * Punishes an actor, by reducing its points. If the actor reaches zero points,
	 * it gets removed from this set of actors.
	 * 
	 * @param actor the actor to punish
	 * @param points how many points get removed
	 * @return true if and only if the actor has reached zero points and has consequently been removed
	 */
	boolean punish(A actor, long points) {
		synchronized (actors) {
			if (actors.computeIfPresent(actor, (__, oldPoints) -> Math.max(0L, oldPoints - points)) == 0) {
				actors.remove(actor);
				return true;
			}
		}

		return false;
	}

	/**
	 * Adds the given actor from this container, if it is not already there.
	 * Otherwise, nothing is added.
	 * 
	 * @param actor the actor to add
	 */
	void add(A actor) {
		synchronized (actors) {
			actors.put(actor, initialPoints);
		}
	}
}