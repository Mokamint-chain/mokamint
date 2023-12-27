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

import java.net.URI;
import java.util.Optional;

import io.hotmoka.websockets.server.api.WebSocketServer;
import io.mokamint.node.api.PublicNode;
import io.mokamint.node.api.Whisperer;

/**
 * A websocket server for the public API of a Mokamint node.
 */
public interface PublicNodeService extends WebSocketServer, Whisperer {
	
	/**
	 * The network endpoint path where {@link PublicNode#getInfo()} is published.
	 */
	String GET_INFO_ENDPOINT = "/get_info";

	/**
	 * The network endpoint path where the {@link PublicNode#getPeerInfos()} method is published.
	 */
	String GET_PEER_INFOS_ENDPOINT = "/get_peer_infos";

	/**
	 * The network endpoint path where the {@link PublicNode#getMinerInfos()} method is published.
	 */
	String GET_MINER_INFOS_ENDPOINT = "/get_miner_infos";

	/**
	 * The network endpoint path where the {@link PublicNode#getTaskInfos()} method is published.
	 */
	String GET_TASK_INFOS_ENDPOINT = "/get_task_infos";

	/**
	 * The network endpoint path where the {@link PublicNode#getBlock(byte[])} method is published.
	 */
	String GET_BLOCK_ENDPOINT = "/get_block";

	/**
	 * The network endpoint path where the {@link PublicNode#getBlockDescription(byte[])} method is published.
	 */
	String GET_BLOCK_DESCRIPTION_ENDPOINT = "/get_block_description";

	/**
	 * The network endpoint path where the {@link PublicNode#getConfig()} method is published.
	 */
	String GET_CONFIG_ENDPOINT = "/get_config";

	/**
	 * The network endpoint path where {@link PublicNode#getChainInfo()} is published.
	 */
	String GET_CHAIN_INFO_ENDPOINT = "/get_chain_info";

	/**
	 * The network endpoint path where {@link PublicNode#getChainPortion(long, int)} is published.
	 */
	String GET_CHAIN_PORTION_ENDPOINT = "/get_chain_portion";

	/**
	 * The network endpoint path where {@link PublicNode#add(io.mokamint.node.api.Transaction)} is published.
	 */
	String ADD_TRANSACTION_ENDPOINT = "/add_transaction";

	/**
	 * The network endpoint path where {@link PublicNode#getMempoolInfo()} is published.
	 */
	String GET_MEMPOOL_INFO_ENDPOINT = "/get_mempool_info";

	/**
	 * The network endpoint path where {@link PublicNode#getMempoolPortion(int, int)} is published.
	 */
	String GET_MEMPOOL_PORTION_ENDPOINT = "/get_mempool_portion";

	/**
	 * The network endpoint path used to whisper a peer between a public node service
	 * and its connected remotes.
	 */
	String WHISPER_PEER_ENDPOINT = "/whisper_peer";

	/**
	 * The network endpoint path used to whisper a block between a public node service
	 * and its connected remotes.
	 */
	String WHISPER_BLOCK_ENDPOINT = "/whisper_block";

	/**
	 * The network endpoint path used to whisper a transaction between a public node service
	 * and its connected remotes.
	 */
	String WHISPER_TRANSACTION_ENDPOINT = "/whisper_transaction";

	/**
	 * Yields the URI under which this service is seen from the outside, if this
	 * is possible. There is no guarantee that this URI is public and available
	 * outside the local network where the service is running.
	 * 
	 * @return the URI, if any
	 */
	Optional<URI> getURI();

	@Override
	void close() throws InterruptedException;
}