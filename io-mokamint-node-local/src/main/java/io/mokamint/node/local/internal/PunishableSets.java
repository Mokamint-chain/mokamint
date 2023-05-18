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

import java.util.function.Function;
import java.util.stream.Stream;

/**
 * A provider of punishable sets.
 */
public interface PunishableSets {

	/**
	 * Creates a new punishable set of actors.
	 * 
	 * @param actors the actors initially contained in the set
	 * @param pointInitializer the initial points assigned to each actor when it is added to the set; this
	 *                         function will be used also when adding a new actor to the set later
	 *                         (see @link {@link PunishableSet#add(Object)})
	 */
	static <A> PunishableSet<A> of(Stream<A> actors, Function<A, Long> pointInitializer) {
		return new PunishableSetImpl<A>(actors, pointInitializer);
	}
}