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
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;

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
public class PunishableSetWithValueImpl<A, V> implements PunishableSetWithValue<A, V> {

	/**
	 * The adapted punishable set.
	 */
	private final PunishableSet<A> parent;

	/**
	 * The values associated to actors.
	 */
	private final Map<A, V> values;

	/**
	 * The function used to initialize the value of newly added actors.
	 */
	private final Function<A, V> valueInitializer;

	/**
	 * Creates a new punishable set of actors with associated value, by adapting a punishable set of actors.
	 * 
	 * @param parent the punishable set of actors that gets adapted. If {@code parent} gets modified later,
	 *               changes are not reflected to this adaptation
	 * @param valueInitializer the initial values assigned to each actor when it is added to this container;
	 *                         this function is used also when adding new actors to the set later
	 *                         (see @link {@link PunishableSet#add(Object)})
	 */
	public PunishableSetWithValueImpl(PunishableSet<A> parent, Function<A, V> valueInitializer) {
		this.parent = parent;
		this.valueInitializer = valueInitializer;
		this.values = new HashMap<>();
		parent.forEach(actor -> values.put(actor, valueInitializer.apply(actor)));
	}

	/**
	 * Copy constructor.
	 * 
	 * @param parent the copied object
	 */
	private PunishableSetWithValueImpl(PunishableSetWithValueImpl<A, V> parent) {
		synchronized (parent.values) {
			this.parent = parent.snapshot();
			this.values = new HashMap<>(parent.values);
			this.valueInitializer = parent.valueInitializer;
		}
	}

	@Override
	public void forEachEntry(BiConsumer<A, V> action) {
		var copy = new PunishableSetWithValueImpl<>(this);
		copy.forEach(actor -> action.accept(actor, copy.values.get(actor)));
	}

	@Override
	public boolean punish(A actor, long points) {
		synchronized (values) {
			if (parent.punish(actor, points)) {
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
			parent.add(actor);
			values.computeIfAbsent(actor, valueInitializer);
		}
	}

	@Override
	public void add(A actor, V value) {
		synchronized (values) {
			parent.add(actor);
			values.put(actor, value);
		}
	}

	@Override
	public void replace(A actor, V value) {
		synchronized (values) {
			values.replace(actor, value);
		}
	}

	@Override
	public boolean contains(A actor) {
		return parent.contains(actor);
	}

	@Override
	public boolean isEmpty() {
		return parent.isEmpty();
	}

	@Override
	public void forEach(Consumer<A> what) {
		parent.forEach(what);
	}

	@Override
	public PunishableSetWithValue<A, V> snapshot() {
		return new PunishableSetWithValueImpl<>(this);
	}
}