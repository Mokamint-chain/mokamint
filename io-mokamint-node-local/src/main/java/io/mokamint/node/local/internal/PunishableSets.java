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

import java.util.function.Predicate;
import java.util.function.ToLongFunction;
import java.util.stream.Stream;

/**
 * A provider of punishable sets.
 */
public abstract class PunishableSets {

	private PunishableSets() {}

	/**
	 * Creates a new punishable set of actors.
	 * 
	 * @param actors the actors initially contained in the set
	 * @param pointInitializer the initial points assigned to each actor when it is added to the set; this
	 *                         function will be used also when adding a new actor to the set later
	 *                         (see @link {@link PunishableSet#add(Object)})
	 */
	public static <A> PunishableSet<A> of(Stream<A> actors, ToLongFunction<A> pointInitializer) {
		return new PunishableSetImpl<>(actors, pointInitializer);
	}

	/**
	 * The filter applied when trying to add an actor to a set.
	 *
	 * @param <A> the type of the actors
	 */
	public interface OnAdd<A> {

		/**
		 * Determines if the given actor must be added.
		 * 
		 * @param actor the actor
		 * @param force true if and only if the addition is strongly required (which is implementation-dependent)
		 * @return true if and only if the addition is allowed
		 */
		boolean apply(A actor, boolean force);
	}

	/**
	 * Creates a new punishable set of actors.
	 * 
	 * @param actors the actors initially contained in the set
	 * @param pointInitializer the initial points assigned to each actor when it is added to the set; this
	 *                         function will be used also when adding a new actor to the set later
	 *                         (see @link {@link PunishableSet#add(Object)})
	 * @param onAdd a filter invoked when trying to add a new actor to the set; only if it yields true, the
	 *              actor will be added
	 * @param onRemove a filter invoked when trying to remove an actor from the set; only if it yields
	 *                 true the actor is removed
	 */
	public static <A> PunishableSet<A> of(Stream<A> actors, ToLongFunction<A> pointInitializer, OnAdd<A> onAdd, Predicate<A> onRemove) {
		return new PunishableSetImpl<>(actors, pointInitializer, onAdd, onRemove);
	}
}