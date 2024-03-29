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
import java.util.function.Consumer;
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
public class PunishableSet<A> {

	/**
	 * The container of the actors. Each actor is mapped to its points. When an actor
	 * gets punished, its points are reduced, until they reach zero and the actor is discarded.
	 */
	private final ConcurrentMap<A, Long> actors = new ConcurrentHashMap<>();

	/**
	 * The initial points assigned to a new actor.
	 */
	private final long initialPoints;

	/**
	 * Creates a new punishable set of actors.
	 * 
	 * @param actors the actors initially contained in the set
	 * @param initialPoints the initial points assigned to each actor when it is added to the set; this
	 *                      will be used also when adding a new actor to the set later
	 *                      (see @link {@link PunishableSet#add(Object)}); moreover, this is used
	 *                      as maximal value in {@link PunishableSet#pardon(Object, long)}
	 * @throws IllegalArgumentException if {@code initialPoints} is not positive
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
	 * Runs some code on each actor in this set. This is weakly consistent,
	 * in the sense that the set of actors can be modified in the meantime and there is
	 * no guarantee that the code will be run for such newly added actors.
	 * 
	 * @param action the code to execute for each actor
	 */
	public void forEach(Consumer<A> action) {
		getElements().forEach(action::accept);
	}

	/**
	 * Punishes an actor, by reducing its points. If the actor reaches zero points,
	 * it gets removed from this set of actors. If the actor was not present in this
	 * container, nothing happens.
	 * 
	 * @param actor the actor to punish
	 * @param points how many points get removed
	 * @return true if and only if the actor was present in this container,
	 *         has reached zero points and has been removed
	 * @throws IllegalArgumentException if {@code points} is negative
	 */
	public boolean punish(A actor, long points) {
		if (points < 0)
			throw new IllegalArgumentException("points cannot be negative");

		var result = new AtomicBoolean();

		actors.computeIfPresent(actor, (a, oldPoints) -> {
			var newPoints = oldPoints - points;
			if (newPoints > 0L)
				return newPoints;
			else {
				result.set(true);
				return null; // which means remove it
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
	 * @param points how many points get pardoned
	 * @return the number of points that have been actually gained by the {@code actor};
	 *         if the {@code actor} had already the maximal number of points or if the
	 *         {@code actor} was not in this container, this return value is 0
	 * @throws IllegalArgumentException if {@code points} is negative
	 */
	public long pardon(A actor, long points) {
		if (points < 0)
			throw new IllegalArgumentException("points cannot be negative");

		var oldPoints = new AtomicLong(0L);
		Long newPoints = actors.computeIfPresent(actor, (a, old) -> {
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
	 * with an implementation specific policy.
	 * 
	 * @param actor the actor to add
	 * @return true if and only if the actor was not present and has been added
	 */
	public final boolean add(A actor) {
		var result = new AtomicBoolean();

		actors.computeIfAbsent(actor, a -> {
			result.set(true);
			return initialPoints;
		});

		return result.get();
	}
}