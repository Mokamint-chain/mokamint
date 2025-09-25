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
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.math.BigInteger;
import java.nio.file.Path;
import java.security.InvalidKeyException;
import java.security.KeyPair;
import java.security.NoSuchAlgorithmException;
import java.security.PublicKey;
import java.security.SignatureException;
import java.util.Arrays;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import io.hotmoka.crypto.HashingAlgorithms;
import io.hotmoka.crypto.SignatureAlgorithms;
import io.hotmoka.crypto.api.SignatureAlgorithm;
import io.hotmoka.testing.AbstractLoggedTests;
import io.mokamint.application.Infos;
import io.mokamint.application.api.Application;
import io.mokamint.application.api.ClosedApplicationException;
import io.mokamint.miner.MiningSpecifications;
import io.mokamint.miner.api.ClosedMinerException;
import io.mokamint.miner.api.Miner;
import io.mokamint.miner.api.MiningSpecification;
import io.mokamint.node.api.ApplicationTimeoutException;
import io.mokamint.node.api.Block;
import io.mokamint.node.api.ClosedNodeException;
import io.mokamint.node.api.NonGenesisBlock;
import io.mokamint.node.local.AbstractLocalNode;
import io.mokamint.node.local.LocalNodeConfigBuilders;
import io.mokamint.node.local.api.LocalNodeConfig;
import io.mokamint.nonce.Deadlines;
import io.mokamint.nonce.Prologs;
import io.mokamint.nonce.api.Challenge;
import io.mokamint.nonce.api.Deadline;
import io.mokamint.nonce.api.Prolog;
import io.mokamint.plotter.Plots;
import io.mokamint.plotter.api.IncompatibleChallengeException;
import io.mokamint.plotter.api.Plot;

public class EventsTests extends AbstractLoggedTests {

	/**
	 * The application of the node used for testing.
	 */
	private static Application app;

	/**
	 * The chain identifier of the deadlines.
	 */
	private final static String CHAIN_ID = "octopus";

	/**
	 * The mining specification of the deadlines.
	 */
	private final static MiningSpecification MINING_SPECIFICATION;

	/**
	 * The keys of the node.
	 */
	private static KeyPair nodeKeys;

	/**
	 * The keys of the plot.
	 */
	private static KeyPair plotKeys;

	/**
	 * The prolog of the deadlines.
	 */
	private static Prolog prolog;

	/**
	 * A plot used for creating deadlines.
	 */
	private static Plot plot;

	static {
		try {
			var ed25519 = SignatureAlgorithms.ed25519();
			MINING_SPECIFICATION = MiningSpecifications.of("name", "description", CHAIN_ID, HashingAlgorithms.shabal256(), ed25519, ed25519, ed25519.getKeyPair().getPublic());
		}
		catch (NoSuchAlgorithmException e) {
			throw new RuntimeException(e);
		}
	}

	@BeforeAll
	public static void beforeAll(@TempDir Path dir) throws Exception {
		app = mock(Application.class);
		when(app.checkDeadline(any())).thenReturn(true);
		var stateHash = new byte[] { 1, 2, 3 };
		when(app.getInitialStateId()).thenReturn(stateHash);
		when(app.endBlock(anyInt(), any())).thenReturn(stateHash);
		var info = Infos.of("name", "description");
		when(app.getInfo()).thenReturn(info);
		var ed25519 = SignatureAlgorithms.ed25519();
		nodeKeys = ed25519.getKeyPair();
		plotKeys = ed25519.getKeyPair();
		prolog = Prologs.of(CHAIN_ID, ed25519, nodeKeys.getPublic(), ed25519, plotKeys.getPublic(), new byte[0]);
		plot = Plots.create(dir.resolve("plot.plot"), prolog, 0, 500, mkConfig(dir).getHashingForDeadlines(), __ -> {});
	}

	@AfterAll
	public static void afterAll() throws Exception {
		plot.close();
	}

	private static LocalNodeConfig mkConfig(Path dir) throws NoSuchAlgorithmException {
		return LocalNodeConfigBuilders.defaults()
			.setDir(dir)
			.setChainId("octopus")
			.setDeadlineWaitTimeout(1000) // a short time is OK for testing
			.build();
	}

	@Test
	@DisplayName("if a deadline is requested and a miner produces a valid deadline, a block is discovered")
	public void discoverNewBlockAfterDeadlineRequestToMiner(@TempDir Path dir) throws Exception {
		var semaphore = new Semaphore(0);

		var myMiner = new Miner() {

			private byte[] deadlineValue;

			@Override
			public void requestDeadline(Challenge challenge, Consumer<Deadline> onDeadlineComputed) {
				try {
					Deadline deadline = plot.getSmallestDeadline(challenge, plotKeys.getPrivate());
					deadlineValue = deadline.getValue();
					onDeadlineComputed.accept(deadline);
				}
				catch (InterruptedException e) {
					Thread.currentThread().interrupt();
				}
				catch (IncompatibleChallengeException | IOException | InvalidKeyException | SignatureException e) {
					throw new RuntimeException("Unexpected exception", e);
				}
			}

			@Override
			public void close() {}

			@Override
			public String toString() {
				return "test miner";
			}

			@Override
			public UUID getUUID() {
				return UUID.randomUUID();
			}

			@Override
			public MiningSpecification getMiningSpecification() {
				return MINING_SPECIFICATION;
			}

			@Override
			public Optional<BigInteger> getBalance(SignatureAlgorithm signature, PublicKey key) throws ClosedMinerException, InterruptedException {
				return Optional.empty();
			}
		};

		class MyLocalNode extends AbstractLocalNode {

			private MyLocalNode() throws InterruptedException, NoSuchAlgorithmException, ClosedNodeException, ClosedApplicationException, ApplicationTimeoutException {
				super(mkConfig(dir), nodeKeys, app, true);
				add(myMiner);
			}

			@Override
			protected void onMined(NonGenesisBlock block) {
				super.onMined(block);
				if (Arrays.equals(block.getDescription().getDeadline().getValue(), myMiner.deadlineValue))
					semaphore.release();
			}
		}

		try (var node = new MyLocalNode()) {
			assertTrue(semaphore.tryAcquire(1, 20, TimeUnit.SECONDS));
		}
	}

	@Test
	@DisplayName("if a deadline is requested and a miner produces an invalid deadline, the misbehavior is signalled to the node")
	public void signalIfInvalidDeadline(@TempDir Path dir) throws Exception {
		var semaphore = new Semaphore(0);

		var myMiner = new Miner() {
	
			@Override
			public void requestDeadline(Challenge description, Consumer<Deadline> onDeadlineComputed) {
				try {
					var deadline = plot.getSmallestDeadline(description, plotKeys.getPrivate());
					var illegalDeadline = Deadlines.of(
							deadline.getProlog(),
							Math.abs(deadline.getProgressive() + 1), deadline.getValue(),
							deadline.getChallenge(),
							plotKeys.getPrivate());

					onDeadlineComputed.accept(illegalDeadline);
				}
				catch (InterruptedException e) {
					Thread.currentThread().interrupt();
				}
				catch (IncompatibleChallengeException | IOException | InvalidKeyException | SignatureException e) {
					throw new RuntimeException("Unexpected exception", e);
				}
			}

			@Override
			public UUID getUUID() {
				return UUID.randomUUID();
			}

			@Override
			public String toString() {
				return "test miner";
			}

			@Override
			public MiningSpecification getMiningSpecification() {
				return MINING_SPECIFICATION;
			}

			@Override
			public Optional<BigInteger> getBalance(SignatureAlgorithm signature, PublicKey key) throws ClosedMinerException, InterruptedException {
				return Optional.empty();
			}

			@Override
			public void close() {}
		};
	
		class MyLocalNode extends AbstractLocalNode {
	
			private MyLocalNode() throws NoSuchAlgorithmException, InterruptedException, ClosedNodeException, ClosedApplicationException, ApplicationTimeoutException {
				super(mkConfig(dir), nodeKeys, app, true);
				add(myMiner);
			}
	
			@Override
			protected void onIllegalDeadlineComputed(Deadline deadline, Miner miner) {
				super.onIllegalDeadlineComputed(deadline, miner);
				if (miner == myMiner)
					semaphore.release();
			}
		}
	
		try (var node = new MyLocalNode()) {
			assertTrue(semaphore.tryAcquire(1, 20, TimeUnit.SECONDS));
		}
	}

	@Test
	@DisplayName("if a node has no miners, an event is signalled")
	public void signalIfNoMiners(@TempDir Path dir) throws Exception {
		var semaphore = new Semaphore(0);

		class MyLocalNode extends AbstractLocalNode {

			private MyLocalNode() throws NoSuchAlgorithmException, InterruptedException, ClosedNodeException, ClosedApplicationException, ApplicationTimeoutException {
				super(mkConfig(dir), nodeKeys, app, true);
			}

			@Override
			protected void onNoMinersAvailable() {
				super.onNoMinersAvailable();
				semaphore.release();	
			}
		}

		try (var node = new MyLocalNode()) {
			assertTrue(semaphore.tryAcquire(1, 1, TimeUnit.SECONDS));
		}
	}

	@Test
	@DisplayName("if miners do not produce any deadline, an event is signalled to the node")
	public void signalIfNoDeadlineArrives(@TempDir Path dir) throws Exception {
		var config = mkConfig(dir);
		var semaphore = new Semaphore(0);

		class MyLocalNode extends AbstractLocalNode {

			private MyLocalNode() throws InterruptedException, ClosedNodeException, ClosedApplicationException, ApplicationTimeoutException {
				super(config, nodeKeys, app, true);

				var miner = mock(Miner.class);
				var uuid = UUID.randomUUID();
				when(miner.getUUID()).thenReturn(uuid);
				when(miner.toString()).thenReturn("a miner");
				add(miner);
			}

			@Override
			protected void onNoDeadlineFound(Block previous) {
				super.onNoDeadlineFound(previous);
				semaphore.release();
			}
		}

		try (var node = new MyLocalNode()) {
			assertTrue(semaphore.tryAcquire(1, 3 * config.getDeadlineWaitTimeout(), TimeUnit.SECONDS));
		}
	}

	@Test
	@DisplayName("if a miner provides deadlines for the wrong chain id, an event is signalled to the node")
	public void signalIfDeadlineForWrongChainIdArrives(@TempDir Path dir) throws Exception {
		var semaphore = new Semaphore(0);
		var config = mkConfig(dir);

		var myMiner = new Miner() {

			@Override
			public void requestDeadline(Challenge challenge, Consumer<Deadline> onDeadlineComputed) {
				try {
					var deadline = plot.getSmallestDeadline(challenge, plotKeys.getPrivate());
					var prolog = deadline.getProlog();
					var illegalDeadline = Deadlines.of(
							Prologs.of(prolog.getChainId() + "!", prolog.getSignatureForBlocks(), prolog.getPublicKeyForSigningBlocks(),
							prolog.getSignatureForDeadlines(), prolog.getPublicKeyForSigningDeadlines(), prolog.getExtra()),
							deadline.getProgressive(), deadline.getValue(),
							deadline.getChallenge(), plotKeys.getPrivate());

					onDeadlineComputed.accept(illegalDeadline);
				}
				catch (InterruptedException e) {
					Thread.currentThread().interrupt();
				}
				catch (IncompatibleChallengeException | IOException | InvalidKeyException | SignatureException e) {
					throw new RuntimeException("Unexpected exception", e);
				}
			}

			@Override
			public UUID getUUID() {
				return UUID.randomUUID();
			}

			@Override
			public MiningSpecification getMiningSpecification() {
				return MINING_SPECIFICATION;
			}

			@Override
			public Optional<BigInteger> getBalance(SignatureAlgorithm signature, PublicKey key) throws ClosedMinerException, InterruptedException {
				return Optional.empty();
			}

			@Override
			public String toString() {
				return "test miner";
			}

			@Override
			public void close() {}
		};

		class MyLocalNode extends AbstractLocalNode {

			private MyLocalNode() throws InterruptedException, ClosedNodeException, ClosedApplicationException, ApplicationTimeoutException {
				super(config, nodeKeys, app, true);
				add(myMiner);
			}

			@Override
			protected void onIllegalDeadlineComputed(Deadline deadline, Miner miner) {
				super.onIllegalDeadlineComputed(deadline, miner);
				semaphore.release();
			}
		}

		try (var node = new MyLocalNode()) {
			assertTrue(semaphore.tryAcquire(1, 20, TimeUnit.SECONDS));
		}
	}
}