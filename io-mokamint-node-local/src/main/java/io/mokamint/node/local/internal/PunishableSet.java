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

import java.util.Map.Entry;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Stream;

import io.hotmoka.annotations.ThreadSafe;

/**
 * A set of actors that can be punished and potentially removed from the set,
 * if they have been punished too much.
 * 
 * @param <A> the type of the actors
 */
@ThreadSafe
public class PunishableSet<A> {

	/**
	 * The container of the actors. Each actor is mapped to its points. When an actor
	 * gets punished, its points get reduced, until they reach zero and the actor is discarded.
	 */
	private final ConcurrentMap<A, Long> actors = new ConcurrentHashMap<>();

	/**
	 * The initial points assigned to new actors.
	 */
	private final long initialPoints;

	/**
	 * Creates a new punishable set of actors.
	 * 
	 * @param actors the actors initially contained in the set
	 * @param initialPoints the non-negative initial points assigned to each actor when it gets added to the set;
	 *                      this will be used also when adding a new actor to the set later
	 *                      (see {@link #add(Object)}; moreover, this is used
	 *                      as maximal value in {@link #pardon(Object, long)}
	 */
	public PunishableSet(Stream<A> actors, long initialPoints) {
		if (initialPoints <= 0L)
			throw new IllegalArgumentException("initialPoints must be positive");

		this.initialPoints = initialPoints;
		
		actors.forEach(actor -> this.actors.put(actor, initialPoints));
	}

	/**
	 * Checks if the given actor is among those of this container.
	 * 
	 * @param actor the actor
	 * @return true if and only if that condition holds
	 */
	public boolean contains(A actor) {
		return actors.containsKey(actor);
	}

	/**
	 * Checks is this set is empty.
	 * 
	 * @return true if and only if this set is empty
	 */
	public boolean isEmpty() {
		return actors.isEmpty();
	}

	/**
	 * Yields the number of actors in this set.
	 * 
	 * @return the number of actors in this set
	 */
	public int size() {
		return actors.size();
	}

	/**
	 * Yields the elements in this container.
	 * 
	 * @return the elements
	 */
	public Stream<A> getElements() {
		return actors.keySet().stream();
	}

	/**
	 * Yields the entries in this container: actors with associated points.
	 * 
	 * @return the entries
	 */
	public Stream<Entry<A, Long>> getActorsWithPoints() {
		return actors.entrySet().stream();
	}

	/**
	 * Punishes an actor, by reducing its points. If the actor reaches zero points,
	 * it gets removed from this container. If the actor was not present in this
	 * container, nothing happens.
	 * 
	 * @param actor the actor to punish
	 * @param points how many points get removed (non-negative)
	 * @return true if and only if the actor was present in this container,
	 *         has reached zero points and has been removed
	 */
	public boolean punish(A actor, long points) {
		if (points < 0)
			throw new IllegalArgumentException("points cannot be negative");

		var result = new AtomicBoolean();

		actors.computeIfPresent(actor, (_actor, oldPoints) -> {
			var newPoints = oldPoints - points;
			if (newPoints > 0L)
				return newPoints;
			else {
				result.set(true);
				return null; // that is: remove it
			}
		});

		return result.get();
	}

	/**
	 * Pardons an actor, by increasing its points. There might be a maximal
	 * value to the resulting points. If the actor was not present in this
	 * container, nothing happens.
	 * 
	 * @param actor the actor to pardon
	 * @param points the points to pardon (non-negative)
	 * @return the number of points that have been actually gained by the {@code actor};
	 *         if the {@code actor} had already the maximal number of points or if the
	 *         {@code actor} was not in this container, this return value is 0
	 */
	public long pardon(A actor, long points) {
		if (points < 0)
			throw new IllegalArgumentException("points cannot be negative");

		var oldPoints = new AtomicLong(0L);
		Long newPoints = actors.computeIfPresent(actor, (_actor, old) -> {
			oldPoints.set(old);
			return Math.min(initialPoints, old + points);
		});

		if (newPoints != null && newPoints.longValue() > oldPoints.get())
			return newPoints.longValue() - oldPoints.get();
		else
			return 0L;
	}

	/**
	 * Removes the given actor from this container, if it is there.
	 * Otherwise, nothing happens.
	 * 
	 * @param actor the actor to remove
	 * @return true if and only if the actor was present and has been consequently removed
	 */
	public boolean remove(A actor) {
		return actors.remove(actor) != null;
	}

	/**
	 * Adds the given actor to this container, if it is not already there.
	 * Otherwise, nothing happens. The initial points of a new actor get reset
	 * to the parameter {@code initialPoints} passed to {@link #PunishableSet(Stream, long)}.
	 * 
	 * @param actor the actor to add
	 * @return true if and only if the actor was not present and has been added
	 */
	public final boolean add(A actor) {
		var result = new AtomicBoolean();

		actors.computeIfAbsent(actor, _actor -> {
			result.set(true);
			return initialPoints;
		});

		return result.get();
	}
}