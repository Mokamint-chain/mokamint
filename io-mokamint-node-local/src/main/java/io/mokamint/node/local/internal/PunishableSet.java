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
import java.util.function.Consumer;
import java.util.stream.Stream;

import io.hotmoka.annotations.ThreadSafe;

/**
 * A set of actors that can be punished and potentially removed from the set
 * if they have been punished too much.
 * 
 * @param <A> the type of the actors
 */
@ThreadSafe
public interface PunishableSet<A> {

	/**
	 * Checks if the given actor is among those of this container.
	 * 
	 * @param actor the actor
	 * @return true if and only if that condition holds
	 */
	boolean contains(A actor);

	/**
	 * Checks is this set is empty.
	 * 
	 * @return true if and only if this set is empty
	 */
	boolean isEmpty();

	/**
	 * Yields the elements in this container.
	 * 
	 * @return the elements
	 */
	Stream<A> getElements();

	/**
	 * Yields the entries in this container: actors with associated points.
	 * 
	 * @return the entries
	 */
	Stream<Entry<A, Long>> getActorsWithPoints();

	/**
	 * Runs some code on each actor in this set. This is weakly consistent,
	 * in the sense that the set of actors can be modified in the meantime and there is
	 * no guarantee that the code will be run for such newly added actors.
	 * 
	 * @param action the code to execute for each actor
	 */
	void forEach(Consumer<A> action);

	/**
	 * Punishes an actor, by reducing its points. If the actor reaches zero points,
	 * it gets removed from this set of actors. If the actor was not present in this
	 * container, nothing happens.
	 * 
	 * @param actor the actor to punish
	 * @param points how many points get removed
	 * @return true if and only if the actor was present in this container,
	 *         has reached zero points and has been removed
	 */
	boolean punish(A actor, long points);

	/**
	 * Adds the given actor to this container, if it is not already there.
	 * Otherwise, nothing happens. The initial points of a new actor get reset
	 * with an implementation specific policy. This is equivalent to
	 * {@link #add(Object, boolean)} where {@code force} is false.
	 * 
	 * @param actor the actor to add
	 * @return true if and only if the actor was not present and has been added
	 */
	boolean add(A actor);

	/**
	 * Adds the given actor to this container, if it is not already there.
	 * Otherwise, nothing happens. The initial points of a new actor get reset
	 * with an implementation specific policy. It allows to specify
	 * an implementation-specific {@code force} parameter.
	 * 
	 * @param actor the actor to add
	 * @param force forces the addition, if this means something to the implementation
	 * @return true if and only if the actor was not present and has been added
	 */
	boolean add(A actor, boolean force);

	/**
	 * Removes the given actor from this container, if it is there.
	 * Otherwise, nothing happens.
	 * 
	 * @param actor the actor to remove
	 * @return true if and only if the actor was present and has been consequently removed
	 */
	boolean remove(A actor);
}