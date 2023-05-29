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

package io.mokamint.node.service.internal;

import java.util.logging.Level;
import java.util.logging.Logger;

import io.hotmoka.websockets.server.AbstractServerEndpoint;
import jakarta.websocket.CloseReason;
import jakarta.websocket.EndpointConfig;
import jakarta.websocket.Session;
import jakarta.websocket.server.ServerEndpointConfig;
import jakarta.websocket.server.ServerEndpointConfig.Configurator;

/**
 * An endpoint that connects to public node remotes.
 */
public class PublicNodeServiceEndpoint extends AbstractServerEndpoint<PublicNodeServiceImpl> {

	private final static Logger LOGGER = Logger.getLogger(PublicNodeServiceEndpoint.class.getName());

	@Override
    public void onOpen(Session session, EndpointConfig config) {
		//session.addMessageHandler((MessageHandler.Whole<Deadline>) getServer()::processDeadline);
    }

    @Override
	public void onClose(Session session, CloseReason closeReason) {
    }

	@Override
    public void onError(Session session, Throwable throwable) {
		LOGGER.log(Level.SEVERE, "websocket error", throwable);
    }

	static ServerEndpointConfig config(Configurator configurator) {
		return ServerEndpointConfig.Builder.create(PublicNodeServiceEndpoint.class, "/")
			//.encoders(List.of(DeadlineDescriptions.Encoder.class)) // it sends DeadlineDescription's
			//.decoders(List.of(Deadlines.Decoder.class)) // and receives Deadline's back
			.configurator(configurator)
			.build();
	}

	@Override
	protected void setServer(PublicNodeServiceImpl server) {
		super.setServer(server);
	}
}