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

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Path;
import java.security.InvalidKeyException;
import java.security.KeyPair;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.UUID;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.function.Function;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import io.hotmoka.crypto.SignatureAlgorithms;
import io.mokamint.application.api.Application;
import io.mokamint.miner.api.Miner;
import io.mokamint.node.api.ClosedNodeException;
import io.mokamint.node.api.DatabaseException;
import io.mokamint.node.api.NonGenesisBlock;
import io.mokamint.node.local.AlreadyInitializedException;
import io.mokamint.node.local.Config;
import io.mokamint.node.local.internal.LocalNodeImpl;
import io.mokamint.node.local.internal.blockchain.MineNewBlockTask.BlockMinedEvent;
import io.mokamint.node.local.internal.blockchain.MineNewBlockTask.IllegalDeadlineEvent;
import io.mokamint.node.local.internal.blockchain.MineNewBlockTask.NoDeadlineFoundEvent;
import io.mokamint.node.local.internal.blockchain.MineNewBlockTask.NoMinersAvailableEvent;
import io.mokamint.nonce.Deadlines;
import io.mokamint.nonce.Prologs;
import io.mokamint.nonce.api.Deadline;
import io.mokamint.nonce.api.DeadlineDescription;
import io.mokamint.nonce.api.Prolog;
import io.mokamint.plotter.Plots;
import io.mokamint.plotter.api.Plot;

public class EventsTests extends Tests {

	/**
	 * The application of the node used for testing.
	 */
	private static Application app;

	/**
	 * The key of the node.
	 */
	private static KeyPair nodeKey;

	/**
	 * The prolog of the deadlines.
	 */
	private static Prolog prolog;

	/**
	 * A plot used for creating deadlines.
	 */
	private static Plot plot;

	@BeforeAll
	public static void beforeAll(@TempDir Path dir) throws NoSuchAlgorithmException, InvalidKeyException, IOException {
		app = mock(Application.class);
		when(app.prologExtraIsValid(any())).thenReturn(true);
		var id25519 = SignatureAlgorithms.ed25519(Function.identity());
		nodeKey = id25519.getKeyPair();
		prolog = Prologs.of("octopus", nodeKey.getPublic(), id25519.getKeyPair().getPublic(), new byte[0]);
		plot = Plots.create(dir.resolve("plot.plot"), prolog, 0, 100, mkConfig(dir).hashingForDeadlines, __ -> {});
	}

	@AfterAll
	public static void afterAll() throws IOException {
		plot.close();
	}

	private static Config mkConfig(Path dir) throws NoSuchAlgorithmException {
		return Config.Builder.defaults()
			.setDir(dir)
			.setChainId("octopus")
			.setDeadlineWaitTimeout(1000) // a short time is OK for testing
			.build();
	}

	@Test
	@DisplayName("if a deadline is requested and a miner produces a valid deadline, a block is discovered")
	public void discoverNewBlockAfterDeadlineRequestToMiner(@TempDir Path dir) throws InterruptedException, NoSuchAlgorithmException, IOException, URISyntaxException, DatabaseException, AlreadyInitializedException, InvalidKeyException {
		var semaphore = new Semaphore(0);
		var deadlineValue = new byte[] { 0, 0, 0, 0, 1, 0, 0, 0 };

		var myMiner = new Miner() {

			@Override
			public void requestDeadline(DeadlineDescription description, Consumer<Deadline> onDeadlineComputed) {
				// we mock the deadline since we need a very small value in order to discover a block quickly
				Deadline deadline = mock(Deadline.class);
				when(deadline.isValid()).thenReturn(true); // <--
				when(deadline.getProlog()).thenReturn(prolog);
				when(deadline.getData()).thenReturn(description.getData());
				when(deadline.getScoopNumber()).thenReturn(description.getScoopNumber());
				when(deadline.getValue()).thenReturn(deadlineValue);
				when(deadline.getHashing()).thenReturn(description.getHashing());
				when(deadline.matches(description)).thenReturn(true);

				onDeadlineComputed.accept(deadline);
			}

			@Override
			public void close() {}

			@Override
			public UUID getUUID() {
				return UUID.randomUUID();
			}
		};

		class MyLocalNode extends LocalNodeImpl {

			private MyLocalNode() throws NoSuchAlgorithmException, IOException, DatabaseException, InterruptedException, AlreadyInitializedException {
				super(mkConfig(dir), nodeKey, app, true);

				try {
					add(myMiner);
				}
				catch (ClosedNodeException e) {
					// impossible
				}
			}

			@Override
			protected void onSubmit(Event event) {
				if (event instanceof BlockMinedEvent bde) {
					var block = bde.block;
					if (block instanceof NonGenesisBlock ngb && Arrays.equals(ngb.getDeadline().getValue(), deadlineValue))
						semaphore.release();
				}
					
				super.onSubmit(event);
			}
		}

		try (var node = new MyLocalNode()) {
			assertTrue(semaphore.tryAcquire(1, 1, TimeUnit.SECONDS));
		}
	}

	@Test
	@DisplayName("if a deadline is requested and a miner produces an invalid deadline, the misbehavior is signalled to the node")
	public void signalIfInvalidDeadline(@TempDir Path dir) throws InterruptedException, NoSuchAlgorithmException, IOException, URISyntaxException, DatabaseException, AlreadyInitializedException, InvalidKeyException {
		var semaphore = new Semaphore(0);

		var myMiner = new Miner() {
	
			@Override
			public void requestDeadline(DeadlineDescription description, Consumer<Deadline> onDeadlineComputed) {
				try {
					var deadline = plot.getSmallestDeadline(description);
					var illegalDeadline = Deadlines.of(
							deadline.getProlog(),
							Math.abs(deadline.getProgressive() + 1), deadline.getValue(),
							deadline.getScoopNumber(), deadline.getData(), deadline.getHashing());

					onDeadlineComputed.accept(illegalDeadline);
				}
				catch (IOException e) {}
			}

			@Override
			public UUID getUUID() {
				return UUID.randomUUID();
			}

			@Override
			public void close() {}
		};
	
		class MyLocalNode extends LocalNodeImpl {
	
			private MyLocalNode() throws NoSuchAlgorithmException, IOException, DatabaseException, InterruptedException, AlreadyInitializedException {
				super(mkConfig(dir), nodeKey, app, true);

				try {
					add(myMiner);
				}
				catch (ClosedNodeException e) {
					// impossible
				}
			}
	
			@Override
			protected void onSubmit(Event event) {
				if (event instanceof IllegalDeadlineEvent ide && ide.miner == myMiner)
					semaphore.release();
					
				super.onSubmit(event);
			}
		}
	
		try (var node = new MyLocalNode()) {
			assertTrue(semaphore.tryAcquire(1, 1, TimeUnit.SECONDS));
		}
	}

	@Test
	@DisplayName("if a node has no miners, an event is signalled")
	public void signalIfNoMiners(@TempDir Path dir) throws InterruptedException, NoSuchAlgorithmException, IOException, DatabaseException, URISyntaxException, AlreadyInitializedException {
		var semaphore = new Semaphore(0);

		class MyLocalNode extends LocalNodeImpl {

			public MyLocalNode() throws NoSuchAlgorithmException, IOException, DatabaseException, InterruptedException, AlreadyInitializedException {
				super(mkConfig(dir), nodeKey, app, true);
			}

			@Override
			protected void onSubmit(Event event) {
				if (event instanceof NoMinersAvailableEvent)
					semaphore.release();
					
				super.onSubmit(event);
			}
		}

		try (var node = new MyLocalNode()) {
			assertTrue(semaphore.tryAcquire(1, 1, TimeUnit.SECONDS));
		}
	}

	@Test
	@DisplayName("if miners do not produce any deadline, an event is signalled to the node")
	public void signalIfNoDeadlineArrives(@TempDir Path dir) throws InterruptedException, NoSuchAlgorithmException, IOException, URISyntaxException, DatabaseException, AlreadyInitializedException {
		var config = mkConfig(dir);
		var semaphore = new Semaphore(0);

		class MyLocalNode extends LocalNodeImpl {

			private MyLocalNode() throws NoSuchAlgorithmException, DatabaseException, IOException, InterruptedException, AlreadyInitializedException {
				super(config, nodeKey, app, true);

				try {
					add(mock(Miner.class));
				}
				catch (ClosedNodeException e) {
					// impossible
				}
			}

			@Override
			protected void onSubmit(Event event) {
				if (event instanceof NoDeadlineFoundEvent)
					semaphore.release();
					
				super.onSubmit(event);
			}
		}

		try (var node = new MyLocalNode()) {
			assertTrue(semaphore.tryAcquire(1, 3 * config.deadlineWaitTimeout, TimeUnit.SECONDS));
		}
	}

	@Test
	@DisplayName("if a miner provides deadlines for the wrong chain id, an event is signalled to the node")
	public void signalIfDeadlineForWrongChainIdArrives(@TempDir Path dir) throws InterruptedException, NoSuchAlgorithmException, IOException, URISyntaxException, DatabaseException, AlreadyInitializedException {
		var semaphore = new Semaphore(0);
		var config = mkConfig(dir);

		var myMiner = new Miner() {

			@Override
			public void requestDeadline(DeadlineDescription description, Consumer<Deadline> onDeadlineComputed) {
				try {
					var deadline = plot.getSmallestDeadline(description);
					var prolog = deadline.getProlog();
					var illegalDeadline = Deadlines.of(
							Prologs.of(prolog.getChainId() + "!", prolog.getNodePublicKey(), prolog.getPlotPublicKey(), prolog.getExtra()),
							deadline.getProgressive(), deadline.getValue(),
							deadline.getScoopNumber(), deadline.getData(), deadline.getHashing());

					onDeadlineComputed.accept(illegalDeadline);
				}
				catch (IOException | NoSuchAlgorithmException | InvalidKeyException e) {}
			}

			@Override
			public UUID getUUID() {
				return UUID.randomUUID();
			}

			@Override
			public void close() {}
		};

		class MyLocalNode extends LocalNodeImpl {

			private MyLocalNode() throws NoSuchAlgorithmException, DatabaseException, IOException, InterruptedException, AlreadyInitializedException {
				super(config, nodeKey, app, true);

				try {
					add(myMiner);
				}
				catch (ClosedNodeException e) {
					// impossible
				}				
			}

			@Override
			protected void onSubmit(Event event) {
				if (event instanceof IllegalDeadlineEvent)
					semaphore.release();

				super.onSubmit(event);
			}
		}

		try (var node = new MyLocalNode()) {
			assertTrue(semaphore.tryAcquire(1, 1, TimeUnit.SECONDS));
		}
	}
}