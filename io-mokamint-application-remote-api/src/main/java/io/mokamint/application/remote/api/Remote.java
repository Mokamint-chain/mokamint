/*
Copyright 2024 Fausto Spoto

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

package io.mokamint.application.remote.api;

import io.hotmoka.annotations.ThreadSafe;
import io.hotmoka.closeables.api.OnCloseHandler;

/**
 * A remote object that presents a programmatic interface
 * to a service for the API of another object of the same class.
 * 
 * @param <E> the type of the exceptions thrown if the remote behaves incorrectly
 */
@ThreadSafe
public interface Remote<E extends Exception> extends AutoCloseable {

	@Override
	void close() throws E, InterruptedException;

	/**
	 * Takes note that the given code must be executed when this object gets closed.
	 * 
	 * @param handler the code
	 */
	void addOnCloseHandler(OnCloseHandler handler);

	/**
	 * Removes the given code from that executed when this object gets closed.
	 * 
	 * @param handler the code
	 */
	void removeOnCloseHandler(OnCloseHandler handler);
}