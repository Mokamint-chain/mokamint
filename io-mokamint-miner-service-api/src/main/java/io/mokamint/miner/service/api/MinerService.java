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

package io.mokamint.miner.service.api;

import java.util.concurrent.TimeoutException;

import io.hotmoka.websockets.client.api.Remote;
import io.mokamint.miner.api.MinerException;
import io.mokamint.miner.api.MiningSpecification;

/**
 * A websocket client that connects to a remote miner exported
 * by some Mokamint node. It is an adapter of a miner into a web service client.
 */
public interface MinerService extends Remote<MinerException> {

	/**
	 * Yields the specification about the miner connected to this service.
	 * It describes the kind of deadlines that are expected by that miner.
	 * 
	 * @return the mining specification of the miner connected to this service
	 * @throws MinerException if the miner is misbehaving
	 * @throws TimeoutException if the operation does not terminate inside the expected time window
	 * @throws InterruptedException if the operation is interrupted before being completed
	 */
	MiningSpecification getMiningSpecification() throws MinerException, TimeoutException, InterruptedException;

	/**
	 * Closes the service.
	 * 
	 * @throws MinerException if the miner is misbehaving
	 */
	@Override
	void close() throws MinerException;
}