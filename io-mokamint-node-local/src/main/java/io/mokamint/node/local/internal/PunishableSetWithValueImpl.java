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
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.stream.Stream;

import io.hotmoka.annotations.ThreadSafe;

/**
 * A set of actors that can be punished and potentially removed from the set
 * if they have been punished too much. Each actor has an associated value.
 * The constructor specifies a map for the initial points of the actors and
 * another map for the initial values of the actors. Both maps get used also
 * when actors are added at a later time.

 * @param <A> the type of the actors
 * @param <V> the type of the values
 */
@ThreadSafe
public class PunishableSetWithValueImpl<A, V> extends PunishableSetImpl<A> implements PunishableSetWithValue<A, V> {

	/**
	 * The values associated to actors.
	 */
	private final Map<A, V> values;

	/**
	 * The function used to initialize the value of newly added actors.
	 */
	private final Function<A, V> valueInitializer;

	/**
	 * Creates a new punishable set of actors.
	 * 
	 * @param actors the actors initially contained in the set
	 * @param valueInitializer the initial values assigned to each actor when it is added to the set; this
	 *                         function will be used also when adding new actors to the set later
	 *                         (see @link {@link PunishableSet#add(Object)})
	 */
	public PunishableSetWithValueImpl(Stream<A> actors, Function<A, Long> pointInitializer, Function<A, V> valueInitializer) {
		super(actors, pointInitializer);

		this.valueInitializer = valueInitializer;
		this.values = actors.distinct().collect(toMap(actor -> actor, valueInitializer, (_i1, _i2) -> null, HashMap::new));
	}

	@Override
	public void forEachEntry(BiConsumer<A, V> what) {
		Set<A> actors;
		Map<A, V> copy;

		synchronized (values) {
			actors = getActors();
			copy = new HashMap<>(values);
		}

		actors.forEach(actor -> what.accept(actor, copy.get(actor)));
	}

	@Override
	public boolean punish(A actor, long points) {
		synchronized (values) {
			if (super.punish(actor, points)) {
				values.remove(actor);
				return true;
			}
			else
				return false;
		}
	}

	@Override
	public void add(A actor) {
		synchronized (values) {
			super.add(actor);
			values.computeIfAbsent(actor, valueInitializer);
		}
	}

	@Override
	public void add(A actor, V value) {
		synchronized (values) {
			super.add(actor);
			values.put(actor, value);
		}
	}

	@Override
	public void replace(A actor, V value) {
		synchronized (values) {
			values.replace(actor, value);
		}
	}
}