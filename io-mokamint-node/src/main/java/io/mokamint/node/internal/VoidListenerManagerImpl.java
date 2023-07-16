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
import java.util.stream.Stream;

import io.hotmoka.annotations.ThreadSafe;
import io.mokamint.node.VoidListenerManager;

/**
 * Implementation of a listeners management object.
 */
@ThreadSafe
public class VoidListenerManagerImpl implements VoidListenerManager {

	private final CopyOnWriteArrayList<Runnable> listeners = new CopyOnWriteArrayList<>();

	/**
	 * Creates the listeners.
	 */
	public VoidListenerManagerImpl() {}

	@Override
	public void add(Runnable listener) {
		listeners.add(listener);
	}

	@Override
	public void remove(Runnable listener) {
		listeners.remove(listener);
	}

	@Override
	public void notifyAllListeners() {
		listeners.forEach(Runnable::run);
	}

	@Override
	public Stream<Runnable> getListeners() {
		return listeners.stream();
	}
}