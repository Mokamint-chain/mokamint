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

package io.mokamint.node;

import java.util.function.Consumer;
import java.util.stream.Stream;

import io.hotmoka.annotations.ThreadSafe;

/**
 * A listeners management object.
 * 
 * @param <T> the type of the parameter passed to the listeners
 */
@ThreadSafe
public interface ListenerManager<T> {

	/**
	 * Adds the given listener.
	 * 
	 * @param listener the listener
	 */
	void addListener(Consumer<T> listener);

	/**
	 * Removes the given listener.
	 * 
	 * @param listener the listener
	 */
	void removeListener(Consumer<T> listener);

	/**
	 * Notifies all listeners.
	 * 
	 * @param t the object passed to the listeners
	 */
	void notifyAll(T t);

	/**
	 * Yields all listeners in this object.
	 * 
	 * @return all listeners in this object
	 */
	Stream<Consumer<T>> getListeners();
}