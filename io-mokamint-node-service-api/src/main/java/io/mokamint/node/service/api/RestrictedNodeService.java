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

import io.hotmoka.websockets.server.api.WebSocketServer;
import io.mokamint.node.api.RestrictedNode;

/**
 * A websocket server for the restricted API of a Mokamint node.
 */
public interface RestrictedNodeService extends WebSocketServer {

	/**
	 * The network endpoint path where the {@link RestrictedNode#addPeer(io.mokamint.node.api.Peer)} method is published.
	 */
	String ADD_PEER_ENDPOINT = "/add_peer";

	/**
	 * The network endpoint path where the {@link RestrictedNode#removePeer(io.mokamint.node.api.Peer)} method is published.
	 */
	String REMOVE_PEER_ENDPOINT = "/remove_peer";

	@Override
	void close();
}