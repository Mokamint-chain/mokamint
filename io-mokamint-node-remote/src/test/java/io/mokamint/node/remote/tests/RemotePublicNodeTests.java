package io.mokamint.node.remote.tests;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.concurrent.TimeoutException;
import java.util.logging.LogManager;
import java.util.stream.Stream;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import io.mokamint.node.Peers;
import io.mokamint.node.api.Peer;
import io.mokamint.node.messages.GetPeersMessage;
import io.mokamint.node.messages.GetPeersResultMessages;
import io.mokamint.node.remote.RemotePublicNodes;
import jakarta.websocket.DeploymentException;
import jakarta.websocket.Session;

public class RemotePublicNodeTests {
	
	@Test
	@DisplayName("getPeers() works")
	public void getPeersWorks() throws DeploymentException, IOException, URISyntaxException, InterruptedException, TimeoutException {
		var peers1 = new Peer[] { Peers.of(new URI("ws://my.machine:1024")), Peers.of(new URI("ws://your.machine:1025")) };

		class MyServer extends TestServer {

			public MyServer() throws DeploymentException, IOException {
				super(8025);
			}

			@Override
			protected void onGetPeers(GetPeersMessage message, Session session) {
				sendObjectAsync(session, GetPeersResultMessages.of(Stream.of(peers1), message.getId()));
			}
		};

		try (var service = new MyServer(); var remote = RemotePublicNodes.of(new URI("ws://localhost:8025"))) {
			var peers2 = remote.getPeers();
			assertArrayEquals(peers1, peers2.toArray(Peer[]::new));
		}
	}

	static {
		String current = System.getProperty("java.util.logging.config.file");
		if (current == null) {
			// if the property is not set, we provide a default (if it exists)
			URL resource = RemotePublicNodeTests.class.getClassLoader().getResource("logging.properties");
			if (resource != null)
				try (var is = resource.openStream()) {
					LogManager.getLogManager().readConfiguration(is);
				}
				catch (SecurityException | IOException e) {
					throw new RuntimeException("Cannot load logging.properties file", e);
				}
		}
	}
}