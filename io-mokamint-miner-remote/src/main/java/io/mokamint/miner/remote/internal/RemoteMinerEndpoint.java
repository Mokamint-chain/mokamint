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
import io.mokamint.nonce.Deadlines;
import io.mokamint.nonce.api.Deadline;
import jakarta.websocket.CloseReason;
import jakarta.websocket.EndpointConfig;
import jakarta.websocket.MessageHandler;
import jakarta.websocket.Session;
import jakarta.websocket.server.ServerEndpointConfig;
import jakarta.websocket.server.ServerEndpointConfig.Configurator;

public class RemoteMinerEndpoint extends AbstractServerEndpoint<RemoteMinerImpl> {

    @Override
    public void onOpen(Session session, EndpointConfig config) {
    	System.out.print("onOpen ");
    	getServer().addSession(session);
    	session.addMessageHandler((MessageHandler.Whole<Deadline>) this::onDeadlineRequest);
    	System.out.println("done");
    }

    private void onDeadlineRequest(Deadline description) {
    	System.out.println("Received " + description);
    }

    @Override
	public void onClose(Session session, CloseReason closeReason) {
    	System.out.print("onClose " + closeReason + " ");
    	getServer().removeSession(session);
    	System.out.println("done");
    }

	@Override
    public void onError(Session session, Throwable throwable) {
    	throwable.printStackTrace();
    }

	static ServerEndpointConfig config(Configurator configurator) {
		return ServerEndpointConfig.Builder.create(RemoteMinerEndpoint.class, "/")
			.encoders(List.of(DeadlineDescriptions.Encoder.class))
			.decoders(List.of(Deadlines.Decoder.class))
			.configurator(configurator)
			.build();
	}

	@Override
	protected void setServer(RemoteMinerImpl server) {
		super.setServer(server);
	}
}