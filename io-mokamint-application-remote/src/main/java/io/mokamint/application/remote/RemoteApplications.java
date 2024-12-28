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

import java.net.URI;

import io.mokamint.application.api.ApplicationException;
import io.mokamint.application.remote.api.RemoteApplication;
import io.mokamint.application.remote.internal.RemoteApplicationImpl;

/**
 * Providers of remote applications.
 */
public abstract class RemoteApplications {
	private RemoteApplications() {}

	/**
	 * Opens and yields a new remote application for the API of another application
	 * already published as a web service.
	 * 
	 * @param uri the URI of the network service that will get bound to the remote application
	 * @param timeout the time (in milliseconds) allowed for a call to the network service;
	 *                beyond that threshold, a timeout exception is thrown
	 * @return the remote application
	 * @throws ApplicationException if the remote application could not be deployed
	 */
	public static RemoteApplication of(URI uri, int timeout) throws ApplicationException {
		return new RemoteApplicationImpl(uri, timeout);
	}
}