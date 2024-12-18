/*
Copyright 2024 Fausto Spoto

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

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.longThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.net.URI;
import java.nio.file.Path;
import java.security.InvalidKeyException;
import java.security.KeyPair;
import java.security.NoSuchAlgorithmException;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

import io.hotmoka.crypto.HashingAlgorithms;
import io.hotmoka.crypto.SignatureAlgorithms;
import io.hotmoka.testing.AbstractLoggedTests;
import io.mokamint.application.api.Application;
import io.mokamint.application.api.ApplicationException;
import io.mokamint.application.api.UnknownGroupIdException;
import io.mokamint.application.api.UnknownStateException;
import io.mokamint.application.remote.RemoteApplications;
import io.mokamint.application.service.ApplicationServices;
import io.mokamint.miner.local.LocalMiners;
import io.mokamint.node.api.Block;
import io.mokamint.node.api.NodeException;
import io.mokamint.node.local.AbstractLocalNode;
import io.mokamint.node.local.LocalNodeConfigBuilders;
import io.mokamint.node.local.api.LocalNodeConfig;
import io.mokamint.nonce.Prologs;
import io.mokamint.plotter.PlotAndKeyPairs;
import io.mokamint.plotter.Plots;
import io.mokamint.plotter.api.Plot;
import jakarta.websocket.DeploymentException;

/**
 * Tests about the possible failures of the application of a node.
 */
public class ApplicationFailureTests extends AbstractLoggedTests {

	/**
	 * The application of the node used for testing.
	 */
	private static Application app;

	/**
	 * The keys of the node.
	 */
	private static KeyPair nodeKeys;

	/**
	 * The keys of the plot.
	 */
	private static KeyPair plotKeys;

	/**
	 * The plot used by the mining node.
	 */
	private static Plot plot;

	@BeforeAll
	public static void beforeAll(@TempDir Path plotDir) throws NoSuchAlgorithmException, InvalidKeyException, TimeoutException, InterruptedException, ApplicationException, UnknownGroupIdException, IOException {
		app = mock(Application.class);
		when(app.checkPrologExtra(any())).thenReturn(true);
		var stateHash = new byte[] { 1, 2, 4 };
		when(app.getInitialStateId()).thenReturn(stateHash);
		when(app.endBlock(anyInt(), any())).thenReturn(stateHash);
		var ed25519 = SignatureAlgorithms.ed25519();
		nodeKeys = ed25519.getKeyPair();
		plotKeys = ed25519.getKeyPair();
		var prolog = Prologs.of("octopus", ed25519, nodeKeys.getPublic(), ed25519, plotKeys.getPublic(), new byte[0]);
		long start = 65536L;
		long length = 50L;
		plot = Plots.create(plotDir.resolve("plot.plot"), prolog, start, length, HashingAlgorithms.shabal256(), __ -> {});
	}

	private LocalNodeConfig mkConfig(Path chainDir) throws NoSuchAlgorithmException {
		return LocalNodeConfigBuilders.defaults()
			.setDir(chainDir)
			.setChainId("octopus")
			.setTargetBlockCreationTime(300)
			.build();
	}

	@Test
	@DisplayName("if the application fails temporarily, the node resumes mining")
	@Timeout(20)
	public void ifApplicationFailsTemporarilyThenNodeRestartsMining(@TempDir Path chain) throws NoSuchAlgorithmException, InterruptedException, DeploymentException, IOException, ApplicationException, NodeException, TimeoutException, UnknownStateException {
		var port = 8032;
		var uri = URI.create("ws://localhost:" + port);
		var sevenBlocksAdded = new Semaphore(0);

		class MyLocalNode extends AbstractLocalNode {

			private MyLocalNode(LocalNodeConfig config, Application app) throws InterruptedException, NodeException, TimeoutException {
				super(config, nodeKeys, app, true);
				add(LocalMiners.of(PlotAndKeyPairs.of(plot, plotKeys)));
			}

			@Override
			protected void onAdded(Block block) {
				super.onAdded(block);
				if (block.getDescription().getHeight() >= 6L)
					sevenBlocksAdded.release();
			}
		}

		var stopDone = new AtomicInteger(0);
		// we simulate an application failure at the fifth block, but only at the first attempts
		when(app.beginBlock(longThat(l -> l == 4 && stopDone.getAndIncrement() <= 1), any(), any())).thenThrow(new TimeoutException("stopped at fifth"));

		try (var service = ApplicationServices.open(app, port);
			 var remote = RemoteApplications.of(uri, 2000);
			 var node = new MyLocalNode(mkConfig(chain), remote)) {

			// we wait until seven blocks get added
			assertTrue(sevenBlocksAdded.tryAcquire(1, 20, TimeUnit.SECONDS));
		}
	}
}