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

/**
 * A provider of punishable sets where each actor is associated with a value.
 */
public interface PunishableSetWithValues {

	/**
	 * Creates a new punishable set of actors with value, by adapting a parent punishable set of actors.
	 * 
	 * @param parent the punishable set of actors that gets adapted. If {@code parent} gets modified later,
	 *               changes are not reflected to this adaptation
	 * @param valueInitializer the initial values assigned to each actor when it is added to the container;
	 *                         this function is used also when adding new actors to the set later
	 *                         (see @link {@link PunishableSet#add(Object)})
	 */
	static <A, V> PunishableSetWithValue<A, V> adapt(PunishableSet<A> parent, Function<A, V> valueInitializer) {
		return new PunishableSetWithValueImpl<A, V>(parent, valueInitializer);
	}
}