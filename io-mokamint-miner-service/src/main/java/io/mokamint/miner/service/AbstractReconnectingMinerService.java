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

import io.mokamint.miner.api.Miner;
import io.mokamint.miner.service.internal.ReconnectingMinerServiceImpl;

/**
 * Abstract implementation of a client that connects to a remote miner.
 * It is an adapter of a miner into a web service client.
 * It tries to keep the connection active, by keeping an internal service
 * that gets recreated whenever its connection seems lost.
 */
public abstract class AbstractReconnectingMinerService extends ReconnectingMinerServiceImpl {

	/**
	 * Creates a miner service by adapting the given miner.
	 * 
	 * @param miner the adapted miner; if this is missing, the service won't provide deadlines but
	 *              will anyway connect to the remote miner
	 * @param uri the websockets URI of the remote miner. For instance: {@code ws://my.site.org:8025}
	 * @param timeout the time (in milliseconds) allowed for a call to the remote miner;
	 *                beyond that threshold, a timeout exception is thrown
	 * @param retryInterval the time. in milliseconds, between a failed connection attempt and the subsequent one
	 */
	protected AbstractReconnectingMinerService(Optional<Miner> miner, URI uri, int timeout, int retryInterval) {
		super(miner, uri, timeout, retryInterval);
	}
}