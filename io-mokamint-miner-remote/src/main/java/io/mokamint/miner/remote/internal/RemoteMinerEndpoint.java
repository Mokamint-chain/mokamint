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

package io.mokamint.miner.remote.internal;

import io.hotmoka.websockets.server.AbstractServerEndpoint;
import io.mokamint.nonce.DeadlineDescriptions;
import io.mokamint.nonce.Deadlines;
import io.mokamint.nonce.api.Deadline;
import jakarta.websocket.CloseReason;
import jakarta.websocket.EndpointConfig;
import jakarta.websocket.MessageHandler;
import jakarta.websocket.Session;
import jakarta.websocket.server.ServerEndpointConfig;

/**
 * An endpoint that connects to miner services.
 */
public class RemoteMinerEndpoint extends AbstractServerEndpoint<RemoteMinerImpl> {

	@SuppressWarnings("resource")
	@Override
    public void onOpen(Session session, EndpointConfig config) {
    	getServer().addSession(session);
    	session.addMessageHandler((MessageHandler.Whole<Deadline>) getServer()::processDeadline);
    }

    @Override
	public void onClose(Session session, CloseReason closeReason) {
    	getServer().removeSession(session);
    }

	static ServerEndpointConfig config(RemoteMinerImpl server) {
		return simpleConfig(server, RemoteMinerEndpoint.class, "/", Deadlines.Decoder.class, DeadlineDescriptions.Encoder.class);
	}
}