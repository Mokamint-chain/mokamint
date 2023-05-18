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

import java.util.function.BiConsumer;

import io.hotmoka.annotations.ThreadSafe;

/**
 * A set of actors that can be punished and potentially removed from the set
 * if they have been punished too much. Each actor has an associated value.
 * 
 * @param <A> the type of the actors
 * @param <V> the type of the values
 */
@ThreadSafe
public interface PunishableSetWithValue<A, V> extends PunishableSet<A> {

	/**
	 * Runs some code on each actor in this set and its associated value. This is weakly consistent,
	 * in the sense that the set of actors can be modified in the meantime and there is
	 * no guarantee that the code will be run for newly added actors.
	 * 
	 * @param what the code to execute for each actor and value
	 */
	void forEachEntry(BiConsumer<A, V> what);

	/**
	 * Adds the given actor to this container, if it is not already there.
	 * Otherwise, nothing is added. The initial points of a new actor get reset
	 * with an implementation specific policy.
	 * 
	 * @param actor the actor to add
	 * @param value the initial value of the actor
	 */
	void add(A actor, V value);

	/**
	 * Replaces the value of the given actor from this container, if it is already there.
	 * Otherwise, nothing is set.
	 * 
	 * @param actor the actor whose value must be replaced
	 * @param value the new value set for the actor
	 */
	void replace(A actor, V value);
}