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

package io.mokamint.node.service.api;

import io.mokamint.node.api.PublicNode;
import io.hotmoka.websockets.server.api.WebSocketServer;

/**
 * A websocket server for the public API of a Mokamint node.
 */
public interface PublicNodeService extends WebSocketServer {
	
	/**
	 * The network endpoint path where {@link PublicNode#getInfo()} is published.
	 */
	String GET_INFO_ENDPOINT = "/get_info";

	/**
	 * The network endpoint path where the {@link PublicNode#getPeers()} method is published.
	 */
	String GET_PEERS_ENDPOINT = "/get_peers";

	/**
	 * The network endpoint path where the {@link PublicNode#getBlock(byte[])} method is published.
	 */
	String GET_BLOCK_ENDPOINT = "/get_block";

	/**
	 * The network endpoint path where the {@link PublicNode#getConfig()} method is published.
	 */
	String GET_CONFIG_ENDPOINT = "/get_config";

	/**
	 * The network endpoint path where {@link PublicNode#getChainInfo()} is published.
	 */
	String GET_CHAIN_INFO_ENDPOINT = "/get_chain_info";

	/**
	 * The network endpoint path used to send peer suggestions from a public node service
	 * to the connected node remotes.
	 */
	String SUGGEST_PEERS_ENDPOINT = "/suggest_peers";

	@Override
	void close();
}