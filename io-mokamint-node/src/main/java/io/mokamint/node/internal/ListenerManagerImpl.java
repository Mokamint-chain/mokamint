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

package io.mokamint.node.internal;

import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

import io.hotmoka.annotations.ThreadSafe;
import io.mokamint.node.ListenerManager;

/**
 * Implementation of a listeners management object.
 * 
 * @param <T> the type of the parameter passed to the listeners
 */
@ThreadSafe
public class ListenerManagerImpl<T> implements ListenerManager<T> {

	private final CopyOnWriteArrayList<Consumer<T>> listeners = new CopyOnWriteArrayList<>();

	/**
	 * Creates the listeners.
	 */
	public ListenerManagerImpl() {}

	@Override
	public void addListener(Consumer<T> listener) {
		listeners.add(listener);
	}

	@Override
	public void removeListener(Consumer<T> listener) {
		listeners.remove(listener);
	}

	@Override
	public void notifyAll(T t) {
		for (var listener: listeners)
			listener.accept(t);
	}
}