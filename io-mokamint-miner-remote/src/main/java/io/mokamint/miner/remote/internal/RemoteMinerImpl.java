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

import java.io.IOException;
import java.net.URI;
import java.util.HashSet;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.logging.Logger;

import io.hotmoka.exceptions.UncheckedIOException;
import io.hotmoka.websockets.server.AbstractWebSocketServer;
import io.mokamint.miner.api.Miner;
import io.mokamint.nonce.api.Deadline;
import io.mokamint.nonce.api.DeadlineDescription;
import jakarta.websocket.DeploymentException;
import jakarta.websocket.EncodeException;
import jakarta.websocket.RemoteEndpoint.Basic;
import jakarta.websocket.Session;
import jakarta.websocket.server.ServerEndpointConfig.Configurator;

/**
 * The implementation of a local miner.
 * It uses a set of plot files to find deadlines on-demand.
 */
public class RemoteMinerImpl extends AbstractWebSocketServer implements Miner {
	private final Set<Session> sessions = new HashSet<>();
	private final Object lock = new Object();
	private final static Logger LOGGER = Logger.getLogger(RemoteMinerImpl.class.getName());

	public RemoteMinerImpl(URI uri) throws DeploymentException, IOException {
		var configurator = new MyConfigurator();
    	var container = getContainer();
    	container.addEndpoint(MiningEndpoint.config(configurator));
    	container.start("/miner", 8025);
	}

	@Override
	public void requestDeadline(DeadlineDescription description, BiConsumer<Deadline, Miner> onDeadlineComputed) {
		LOGGER.info("received request " + description);

		sessions.stream()
			.filter(Session::isOpen)
			.map(Session::getBasicRemote)
			.forEach(remote -> send(description, remote));
	}

	private static void send(DeadlineDescription description, Basic remote) {
		try {
			// TODO: this is blocking....
			remote.sendObject(description);
		}
		catch (IOException e) {
			throw new UncheckedIOException(e);
		}
		catch (EncodeException e) {
			throw new RuntimeException(e); // unexpected
		}
	}

	@Override
	public void close() {
		super.close();
	}

	void addSession(Session session) {
		synchronized (lock) {
			sessions.add(session);
		}
	}

	void removeSession(Session session) {
		synchronized (lock) {
			sessions.remove(session);
		}
	}

	private class MyConfigurator extends Configurator {

    	@Override
    	public <T> T getEndpointInstance(Class<T> endpointClass) throws InstantiationException {
            var result = super.getEndpointInstance(endpointClass);
            if (result instanceof MiningEndpoint)
            	((MiningEndpoint) result).setServer(RemoteMinerImpl.this); // we inject the server

            return result;
        }
    }
}