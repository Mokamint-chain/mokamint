/*
Copyright 2025 Fausto Spoto

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

package io.mokamint.miner.service;

import java.net.URI;
import java.util.Optional;
import java.util.function.Consumer;

import io.hotmoka.websockets.api.FailedDeploymentException;
import io.mokamint.miner.api.Miner;
import io.mokamint.miner.service.api.ReconnectingMinerService;
import io.mokamint.miner.service.internal.ReconnectingMinerServiceImpl;

/**
 * Providers of reconnecting miner services.
 */
public final class ReconnectingMinerServices {

	private ReconnectingMinerServices() {}

	/**
	 * Opens and yields a new reconnecting miner service that adapts the given miner and connects it to the given URI of a remote miner.
	 * 
	 * @param miner the adapted miner
	 * @param uri the websockets URI of the remote miner. For instance: {@code ws://my.site.org:8025}
	 * @param timeout the time (in milliseconds) allowed for a call to the remote miner;
	 *                beyond that threshold, a timeout exception is thrown
	 * @param onConnectionFailed a handler called at each connection attempt that fails
	 * @return the miner service
	 */
	public static ReconnectingMinerService of(Miner miner, URI uri, int timeout, Consumer<FailedDeploymentException> onConnectionFailed) {
		return new ReconnectingMinerServiceImpl(Optional.of(miner), uri, timeout, onConnectionFailed);
	}

	/**
	 * Opens and yields a reconnecting miner service without a miner. It won't provide deadlines to the connected
	 * remote miner, but allows one to call the methods of the remote miner anyway.
	 * 
	 * @param uri the websockets URI of the remote miner. For instance: {@code ws://my.site.org:8025}
	 * @param timeout the time (in milliseconds) allowed for a call to the remote miner;
	 *                beyond that threshold, a timeout exception is thrown
	 * @param onConnectionFailed a handler called at each connection attempt that fails
	 * @return the miner service
	 */
	public static ReconnectingMinerService of(URI uri, int timeout, Consumer<FailedDeploymentException> onConnectionFailed) {
		return new ReconnectingMinerServiceImpl(Optional.empty(), uri, timeout, onConnectionFailed);
	}
}