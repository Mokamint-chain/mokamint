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
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.function.ToLongFunction;
import java.util.stream.Stream;

import io.hotmoka.annotations.ThreadSafe;
import io.mokamint.node.local.internal.PunishableSets.OnAdd;

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
	private final Map<A, Long> actors = new HashMap<>();

	/**
	 * The initial points assigned to a new actor.
	 */
	private final ToLongFunction<A> pointInitializer;

	/**
	 * A filter invoked when trying to add a new actor to the set.
	 * Only iff it yields true the actor is actually added.
	 */
	private final OnAdd<A> onAdd;

	/**
	 * A filter invoked when trying to remove an actor from the set.
	 * 
	 */
	private final Predicate<A> onRemove;

	/**
	 * Creates a new punishable set of actors.
	 * 
	 * @param actors the actors initially contained in the set
	 * @param pointInitializer the initial points assigned to each actor when it is added to the set; this
	 *                         function will be used also when adding new actors to the set later
	 *                         (see @link {@link PunishableSet#add(Object)})
	 */
	public PunishableSetImpl(Stream<A> actors, ToLongFunction<A> pointInitializer) {
		this(actors, pointInitializer, (_actor, _force) -> true, _actor -> true);
	}

	/**
	 * Creates a new punishable set of actors.
	 * 
	 * @param actors the actors initially added to the set. Their addition is forced
	 * @param pointInitializer the initial points assigned to each actor when it is added to the set; this
	 *                         function will be used also when adding new actors to the set later
	 *                         (see {@link PunishableSet#add(Object)})
	 * @param onAdd a filter invoked when a new actor is added to the set
	 * @param onRemove a filter invoked when an actor is removed from the set
	 */
	public PunishableSetImpl(Stream<A> actors, ToLongFunction<A> pointInitializer, OnAdd<A> onAdd, Predicate<A> onRemove) {
		this.pointInitializer = pointInitializer;
		this.onAdd = onAdd;
		this.onRemove = onRemove;

		actors.forEach(actor -> add(actor, true));
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
	public Stream<A> getElements() {
		synchronized (actors) {
			return new HashSet<>(actors.keySet()).stream();
		}
	}

	@Override
	public void forEach(Consumer<A> action) {
		Set<A> copy;

		synchronized (actors) {
			copy = new HashSet<>(actors.keySet());
		}

		copy.forEach(action::accept);
	}

	@Override
	public boolean punish(A actor, long points) {
		synchronized (actors) {
			if (contains(actor)) {
				var newPoints = actors.get(actor) - points;
				if (newPoints > 0L)
					actors.put(actor, newPoints);
				else
					return remove(actor);
			}
		}

		return false;
	}

	@Override
	public boolean remove(A actor) {
		synchronized (actors) {
			if (contains(actor) && onRemove.test(actor)) {
				actors.remove(actor);
				return true;
			}
		}
	
		return false;
	}

	@Override
	public boolean add(A actor, boolean force) {
		synchronized (actors) {
			if (!contains(actor)) {
				var initialPoints = pointInitializer.applyAsLong(actor);
				if (initialPoints > 0L && onAdd.apply(actor, force)) {
					actors.put(actor, initialPoints);
					return true;
				}
			}
		}

		return false;
	}

	@Override
	public boolean add(A actor) {
		return add(actor, false);
	}
}