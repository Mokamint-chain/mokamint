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

package io.mokamint.miner.service.api;

/**
 * A websocket client that connects to a remote miner exported
 * by some Mokamint node. It is an adapter of a miner into a web service client.
 * If the network connection to the remote miner goes down, this service
 * tries automatically to reconnect.
 */
public interface ReconnectingMinerService extends MinerService {

	/**
	 * Determines if this miner service is currently connected.
	 * 
	 * @return true if this miner service is currently connected
	 */
	boolean isConnected();
}