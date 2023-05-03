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

package io.mokamint.miner.service.internal;

import java.io.IOException;
import java.net.URI;
import java.util.List;

import org.glassfish.tyrus.client.ClientManager;

import io.hotmoka.websockets.client.AbstractClientEndpoint;
import io.mokamint.nonce.DeadlineDescriptions;
import io.mokamint.nonce.Deadlines;
import io.mokamint.nonce.api.DeadlineDescription;
import jakarta.websocket.ClientEndpointConfig;
import jakarta.websocket.CloseReason;
import jakarta.websocket.DeploymentException;
import jakarta.websocket.EndpointConfig;
import jakarta.websocket.MessageHandler;
import jakarta.websocket.Session;

class MinerServiceEndpoint extends AbstractClientEndpoint<MinerServiceImpl> {

	MinerServiceEndpoint(MinerServiceImpl client) {
		super(client);
	}

	Session deployAt(URI uri) throws DeploymentException, IOException {
		var config = ClientEndpointConfig.Builder.create()
				.encoders(List.of(Deadlines.Encoder.class))
				.decoders(List.of(DeadlineDescriptions.Decoder.class))
				.build();

		return ClientManager.createClient().connectToServer(this, config, uri);
	}

	@Override
	public void onOpen(Session session, EndpointConfig config) {
		session.addMessageHandler((MessageHandler.Whole<DeadlineDescription>) this::messageHandler);
	}

	@Override
	public void onClose(Session session, CloseReason closeReason) {
		System.out.println("onClose " + closeReason);
		getClient().disconnect();
	}

	private void messageHandler(DeadlineDescription description) {
		System.out.println("Received " + description);
	}
}
