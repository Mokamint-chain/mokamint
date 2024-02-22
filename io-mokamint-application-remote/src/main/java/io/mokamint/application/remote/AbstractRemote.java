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

package io.mokamint.application.remote;

import io.hotmoka.annotations.ThreadSafe;
import io.mokamint.application.remote.internal.AbstractRemoteImpl;

/**
 * A partial implementation of a remote object that presents a programmatic interface
 * to a service for the API of another object of the same class.
 */
@ThreadSafe
public abstract class AbstractRemote<E extends Exception> extends AbstractRemoteImpl<E> {

	/**
	 * Creates and opens a new remote application for the API of another application
	 * whose web service is already published.
	 * 
	 * @param timeout the time (in milliseconds) allowed for a call to the network service;
	 *                beyond that threshold, a timeout exception is thrown
	 */
	protected AbstractRemote(long timeout) {
		super(timeout);
	}
}