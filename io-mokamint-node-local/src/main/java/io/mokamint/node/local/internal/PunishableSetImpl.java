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
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Stream;

import io.hotmoka.annotations.ThreadSafe;

/**
 * A set of actors that can be punished and potentially removed from the set
 * if they have been punished too much. The constructor specifies a map
 * for the initial points of the actors, that gets used also when actors
 * are added at a later time.
 * 
 * @param <A> the type of the actors
 */
@ThreadSafe
public class PunishableSetImpl<A> implements PunishableSet<A> {

	/**
	 * The container of the actors. Each actor is mapped to its points. When an actor
	 * gets punished, its points are reduced, until they reach zero and the actor is discarded.
	 */
	private final Map<A, Long> actors;

	/**
	 * The initial points assigned to a new actor.
	 */
	private final Function<A, Long> pointInitializer;

	/**
	 * Creates a new punishable set of actors.
	 * 
	 * @param actors the actors initially contained in the set
	 * @param pointInitializer the initial points assigned to each actor when it is added to the set; this
	 *                         function will be used also when adding new actors to the set later
	 *                         (see @link {@link PunishableSet#add(Object)})
	 */
	public PunishableSetImpl(Stream<A> actors, Function<A, Long> pointInitializer) {
		this.pointInitializer = pointInitializer;
		this.actors = actors.distinct().collect(toMap(actor -> actor, pointInitializer, (_i1, _i2) -> 0L, HashMap::new));
	}

	@Override
	public boolean contains(A actor) {
		synchronized (actors) {
			return actors.containsKey(actor);
		}
	}

	@Override
	public boolean isEmpty() {
		synchronized (actors) {
			return actors.isEmpty();
		}
	}

	@Override
	public void forEach(Consumer<A> what) {
		getActors().forEach(what::accept);
	}

	/**
	 * Yields a snapshot of the actors of this set.
	 * 
	 * @return the actors
	 */
	protected Set<A> getActors() {
		synchronized (actors) {
			return new HashSet<>(actors.keySet());
		}
	}

	@Override
	public boolean punish(A actor, long points) {
		synchronized (actors) {
			if (actors.computeIfPresent(actor, (__, oldPoints) -> Math.max(0L, oldPoints - points)) == 0) {
				actors.remove(actor);
				return true;
			}
		}

		return false;
	}

	@Override
	public void add(A actor) {
		synchronized (actors) {
			actors.computeIfAbsent(actor, pointInitializer);
		}
	}
}