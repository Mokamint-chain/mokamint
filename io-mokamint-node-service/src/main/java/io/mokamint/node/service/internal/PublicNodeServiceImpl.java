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

import java.io.IOException;
import java.util.logging.Logger;

import io.hotmoka.annotations.ThreadSafe;
import io.hotmoka.websockets.server.AbstractWebSocketServer;
import io.mokamint.node.api.PublicNode;
import io.mokamint.node.service.api.PublicNodeService;
import jakarta.websocket.DeploymentException;
import jakarta.websocket.server.ServerEndpointConfig.Configurator;

/**
 * The implementation of a public node service. It publishes an endpoint at a URL,
 * where clients can connect to query the public API of a Mokamint node.
 */
@ThreadSafe
public class PublicNodeServiceImpl extends AbstractWebSocketServer implements PublicNodeService {

	/**
	 * The port of localhost, where this service is listening.
	 */
	private final int port;

	/**
	 * The node whose API is published.
	 */
	private final PublicNode node;

	private final static Logger LOGGER = Logger.getLogger(PublicNodeServiceImpl.class.getName());

	public PublicNodeServiceImpl(PublicNode node, int port) throws DeploymentException, IOException {
		this.port = port;
		this.node = node;
		var configurator = new MyConfigurator();
    	var container = getContainer();
    	container.addEndpoint(PublicNodeServiceEndpoint.config(configurator));
    	container.start("", port);
    	LOGGER.info("published a public node service at ws://localhost:" + port);
	}

	/*
	@Override
	public void requestDeadline(DeadlineDescription description, Consumer<Deadline> onDeadlineComputed) {
		requests.add(description, onDeadlineComputed);

		Set<Session> copy;
		synchronized (sessions) {
			copy = new HashSet<>(sessions);
		}

		LOGGER.info("requesting " + description + " to " + copy.size() + " open sessions");

		copy.stream()
			.filter(Session::isOpen)
			.map(Session::getAsyncRemote)
			.forEach(remote -> remote.sendObject(description));
	}
	*/

	@Override
	public void close() {
		super.close();
		LOGGER.info("closed the public node service at ws://localhost:" + port);
	}

	private class MyConfigurator extends Configurator {

    	@Override
    	public <T> T getEndpointInstance(Class<T> endpointClass) throws InstantiationException {
            var result = super.getEndpointInstance(endpointClass);
            if (result instanceof PublicNodeServiceEndpoint)
            	((PublicNodeServiceEndpoint) result).setServer(PublicNodeServiceImpl.this); // we inject the server

            return result;
        }
    }
}