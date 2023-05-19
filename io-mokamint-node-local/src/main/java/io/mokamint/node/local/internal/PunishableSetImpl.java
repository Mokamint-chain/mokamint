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
	 * A call-back invoked when a new actor is actually added through {@link #add(Object)}.
	 */
	private final Consumer<A> onAdd;

	/**
	 * A call-back invoked when an actor is actually removed through {@link #punish(Object, long)}.
	 */
	private final Consumer<A> onRemove;

	/**
	 * Creates a new punishable set of actors.
	 * 
	 * @param actors the actors initially contained in the set
	 * @param pointInitializer the initial points assigned to each actor when it is added to the set; this
	 *                         function will be used also when adding new actors to the set later
	 *                         (see @link {@link PunishableSet#add(Object)})
	 */
	public PunishableSetImpl(Stream<A> actors, Function<A, Long> pointInitializer) {
		this(actors, pointInitializer, _actor -> {}, _actor -> {});
	}

	/**
	 * Creates a new punishable set of actors.
	 * 
	 * @param actors the actors initially contained in the set
	 * @param pointInitializer the initial points assigned to each actor when it is added to the set; this
	 *                         function will be used also when adding new actors to the set later
	 *                         (see @link {@link PunishableSet#add(Object)})
	 * @param onAdd a call-back invoked when a new actor is actually added through {@link #add(Object)}
	 * @param onRemove a call-back invoked when an actor is actually removed through {@link #punish(Object, long)}
	 */
	public PunishableSetImpl(Stream<A> actors, Function<A, Long> pointInitializer, Consumer<A> onAdd, Consumer<A> onRemove) {
		this.actors = actors.distinct().collect(toMap(actor -> actor, pointInitializer, (_i1, _i2) -> 0L, HashMap::new));
		this.pointInitializer = pointInitializer;
		this.onAdd = onAdd;
		this.onRemove = onRemove;
	}

	/**
	 * Copy constructor.
	 * 
	 * @param original the copied object
	 */
	private PunishableSetImpl(PunishableSetImpl<A> original) {
		synchronized (original.actors) {
			this.actors = new HashMap<>(original.actors);
		}

		this.pointInitializer = original.pointInitializer;
		this.onAdd = original.onAdd;
		this.onRemove = original.onRemove;
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
	public void forEach(Consumer<A> action) {
		snapshot().actors.keySet().forEach(action::accept);
	}

	@Override
	public PunishableSetImpl<A> snapshot() {
		return new PunishableSetImpl<>(this);
	}

	@Override
	public boolean punish(A actor, long points) {
		synchronized (actors) {
			if (contains(actor) && actors.compute(actor, (__, oldPoints) -> Math.max(0L, oldPoints - points)) == 0) {
				actors.remove(actor);
				onRemove.accept(actor);
				return true;
			}
		}

		return false;
	}

	@Override
	public boolean add(A actor) {
		synchronized (actors) {
			if (!contains(actor)) {
				actors.put(actor, pointInitializer.apply(actor));
				onAdd.accept(actor);
				return true;
			}
		}

		return false;
	}
}