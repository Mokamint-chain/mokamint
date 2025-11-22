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

package io.mokamint.miner.service;

import java.net.URI;
import java.util.Optional;

import io.hotmoka.websockets.api.FailedDeploymentException;
import io.mokamint.miner.api.Miner;
import io.mokamint.miner.service.api.MinerService;
import io.mokamint.miner.service.internal.MinerServiceImpl;

/**
 * Providers of miner services.
 */
public final class MinerServices {

	private MinerServices() {}

	/**
	 * Opens and yields a new miner service that adapts the given miner and connects it to the given URI of a remote miner.
	 * 
	 * @param miner the adapted miner
	 * @param uri the websockets URI of the remote miner. For instance: {@code ws://my.site.org:8025}
	 * @param timeout the time (in milliseconds) allowed for a call to the remote miner;
	 *                beyond that threshold, a timeout exception is thrown
	 * @return the miner service
	 * @throws FailedDeploymentException if the service cannot be deployed
	 * @throws InterruptedException if the deployment of the service has been interrupted
	 */
	public static MinerService of(Miner miner, URI uri, int timeout) throws FailedDeploymentException, InterruptedException {
		return new MinerServiceImpl(Optional.of(miner), uri, timeout);
	}

	/**
	 * Opens and yields a miner service without a miner. It won't provide deadlines to the connected
	 * remote miner, but allows one to call the methods of the remote miner anyway.
	 * 
	 * @param uri the websockets URI of the remote miner. For instance: {@code ws://my.site.org:8025}
	 * @param timeout the time (in milliseconds) allowed for a call to the remote miner;
	 *                beyond that threshold, a timeout exception is thrown
	 * @return the miner service
	 * @throws FailedDeploymentException if the service cannot be deployed
	 * @throws InterruptedException if the deployment of the service has been interrupted
	 */
	public static MinerService of(URI uri, int timeout) throws FailedDeploymentException, InterruptedException {
		return new MinerServiceImpl(Optional.empty(), uri, timeout);
	}
}