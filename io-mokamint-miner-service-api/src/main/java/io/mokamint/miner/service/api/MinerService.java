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

import io.hotmoka.websockets.client.api.Remote;
import io.mokamint.miner.api.MinerException;

/**
 * A websocket client that connects to a remote miner exported
 * by some Mokamint node. It is an adapter of a miner into a web service client.
 */
public interface MinerService extends Remote<MinerException> {

	/**
	 * Closes the service.
	 * 
	 * @throws MinerException if the miner is misbehaving
	 */
	@Override
	void close() throws MinerException;
}