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

package io.mokamint.node.local.tests;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Path;
import java.security.NoSuchAlgorithmException;
import java.util.concurrent.TimeoutException;
import java.util.function.Function;
import java.util.logging.LogManager;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

import io.hotmoka.crypto.HashingAlgorithms;
import io.mokamint.application.api.Application;
import io.mokamint.miner.local.LocalMiners;
import io.mokamint.node.Peers;
import io.mokamint.node.api.ClosedNodeException;
import io.mokamint.node.api.DatabaseException;
import io.mokamint.node.api.IncompatiblePeerException;
import io.mokamint.node.local.Config;
import io.mokamint.node.local.LocalNodes;
import io.mokamint.node.local.internal.LocalNodeImpl;
import io.mokamint.node.service.PublicNodeServices;
import io.mokamint.plotter.Plots;
import jakarta.websocket.DeploymentException;

/**
 * Tests about the propagation of the peers in a network of nodes.
 */
public class BlockPropagationTests {

	/**
	 * The application of the node used for testing.
	 */
	private static Application app;

	@BeforeAll
	public static void beforeAll() {
		app = mock(Application.class);
		when(app.prologIsValid(any())).thenReturn(true);
	}

	@Test
	@Disabled
	@DisplayName("a node without mining capacity follows the blocks of a peer")
	@Timeout(1000) // TODO
	public void nodeWithoutMinerFollowsPeer(@TempDir Path chain1, @TempDir Path chain2)
			throws URISyntaxException, NoSuchAlgorithmException, InterruptedException,
				   DatabaseException, IOException, DeploymentException, TimeoutException, IncompatiblePeerException, ClosedNodeException {

		var port2 = 8034;
		var peer2 = Peers.of(new URI("ws://localhost:" + port2));

		var config1 = Config.Builder.defaults()
			.setDir(chain1)
			.setTargetBlockCreationTime(2000L)
			.setInitialAcceleration(1000000000000000L)
			.build();

		var config2 = Config.Builder.defaults()
			.setDir(chain2)
			.setTargetBlockCreationTime(2000L)
			.setInitialAcceleration(1000000000000000L)
			.build();

		class MyLocalNode extends LocalNodeImpl {

			private MyLocalNode(Config config) throws NoSuchAlgorithmException, IOException, DatabaseException {
				super(config, app, false); // <--- does not start mining by itself
			}

			@Override
			protected void onStart(Task task) {
				System.out.println(task);
				super.onStart(task);
			}

			@Override
			protected void onStart(Event event) {
				System.out.println(event);
				super.onStart(event);
			}
		}

		var prolog = new byte[] { 11, 13, 24, 88 };
		long start = 65536L;
		long length = 50L;
		var hashing = HashingAlgorithms.shabal256(Function.identity());

		try (var node1 = new MyLocalNode(config1);
			 var plot2 = Plots.create(chain2.resolve("plot2.plot"), prolog, start, length, hashing, __ -> {});
			 var miner2 = LocalMiners.of(plot2);
			 var node2 = LocalNodes.of(config2, app, true, miner2);
			 var service2 = PublicNodeServices.open(node2, port2)) {

			node1.addPeer(peer2);

			Thread.sleep(10000000L);
		}
	}

	static {
		String current = System.getProperty("java.util.logging.config.file");
		if (current == null) {
			// if the property is not set, we provide a default (if it exists)
			URL resource = BlockPropagationTests.class.getClassLoader().getResource("logging.properties");
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