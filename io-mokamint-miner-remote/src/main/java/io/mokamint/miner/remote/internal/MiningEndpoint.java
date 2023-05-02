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

import java.util.List;

import io.hotmoka.websockets.server.AbstractServerEndpoint;
import io.mokamint.nonce.DeadlineDescriptions;
import jakarta.websocket.CloseReason;
import jakarta.websocket.EndpointConfig;
import jakarta.websocket.Session;
import jakarta.websocket.server.ServerEndpointConfig;
import jakarta.websocket.server.ServerEndpointConfig.Configurator;

public class MiningEndpoint extends AbstractServerEndpoint<RemoteMinerImpl> {

    @Override
    public void onOpen(Session session, EndpointConfig config) {
    	getServer().addSession(session);
    	/*
    	String username = session.getPathParameters().get("username");
    	getServer().setUsername(session.getId(), username);

    	session.addMessageHandler((MessageHandler.Whole<PartialMessage>) (message -> {
    		System.out.println("Received " + message);
    		broadcast(message.setFrom(username), session); // fill in info about the user
    	}));

    	broadcast(Messages.full(username, "connected!"), session);
    	*/
    }

    @Override
	public void onClose(Session session, CloseReason closeReason) {
    	getServer().removeSession(session);
    }

	@Override
    public void onError(Session session, Throwable throwable) {
    	throwable.printStackTrace();
    }

	static ServerEndpointConfig config(Configurator configurator) {
		return ServerEndpointConfig.Builder.create(MiningEndpoint.class, "/")
			.encoders(List.of(DeadlineDescriptions.Encoder.class))
			.configurator(configurator)
			.build();
	}

	@Override
	protected void setServer(RemoteMinerImpl server) {
		super.setServer(server);
	}

	/*private static void send(Message message, Basic remote) {
		try {
			remote.sendObject(message);
		}
		catch (IOException e) {
			throw new UncheckedIOException(e);
		}
		catch (EncodeException e) {
			throw new RuntimeException(e); // unexpected
		}
	}*/
}