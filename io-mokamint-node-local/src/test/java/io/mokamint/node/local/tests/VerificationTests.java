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

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.math.BigInteger;
import java.nio.file.Path;
import java.security.InvalidKeyException;
import java.security.KeyPair;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.SignatureException;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.concurrent.TimeoutException;
import java.util.stream.Stream;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import io.hotmoka.crypto.HashingAlgorithms;
import io.hotmoka.crypto.SignatureAlgorithms;
import io.hotmoka.testing.AbstractLoggedTests;
import io.mokamint.application.api.Application;
import io.mokamint.application.api.ApplicationException;
import io.mokamint.application.api.UnknownGroupIdException;
import io.mokamint.node.BlockDescriptions;
import io.mokamint.node.Blocks;
import io.mokamint.node.Transactions;
import io.mokamint.node.api.GenesisBlock;
import io.mokamint.node.api.NodeException;
import io.mokamint.node.api.NonGenesisBlock;
import io.mokamint.node.api.TransactionRejectedException;
import io.mokamint.node.local.AbstractLocalNode;
import io.mokamint.node.local.AlreadyInitializedException;
import io.mokamint.node.local.LocalNodeConfigBuilders;
import io.mokamint.node.local.api.LocalNodeConfig;
import io.mokamint.node.local.internal.Blockchain;
import io.mokamint.node.local.internal.VerificationException;
import io.mokamint.nonce.Challenges;
import io.mokamint.nonce.Deadlines;
import io.mokamint.nonce.Prologs;
import io.mokamint.nonce.api.Challenge;
import io.mokamint.nonce.api.Prolog;
import io.mokamint.plotter.Plots;
import io.mokamint.plotter.api.Plot;

public class VerificationTests extends AbstractLoggedTests {

	/**
	 * The plot used for creating the deadlines.
	 */
	private static Plot plot;

	/**
	 * The id of the state of the test application.
	 */
	private final static byte[] stateId = new byte[] { 1, 1, 1, 1, 1, 1 };

	/**
	 * The application running in the node.
	 */
	private static Application application;

	/**
	 * The keys of the node.
	 */
	private static KeyPair nodeKeys;

	/**
	 * The private key used to sign the blocks.
	 */
	private static PrivateKey nodePrivateKey;

	/**
	 * The private key used to sign the deadlines.
	 */
	private static PrivateKey plotPrivateKey;

	/**
	 * The prolog of the deadlines.
	 */
	private static Prolog prolog;

	@BeforeAll
	public static void beforeAll(@TempDir Path plotDir) throws IOException, NoSuchAlgorithmException, InvalidKeyException, TransactionRejectedException, TimeoutException, InterruptedException, ApplicationException, UnknownGroupIdException {
		var config = LocalNodeConfigBuilders.defaults().build();
		var signature = config.getSignatureForBlocks();
		nodeKeys = signature.getKeyPair();
		var plotKeys = signature.getKeyPair();

		nodePrivateKey = nodeKeys.getPrivate();
		plotPrivateKey = plotKeys.getPrivate();

		prolog = Prologs.of("octopus", signature, nodeKeys.getPublic(), signature, plotKeys.getPublic(), new byte[0]);
		long start = 65536L;
		long length = 50L;
		plot = Plots.create(plotDir.resolve("plot.plot"), prolog, start, length, HashingAlgorithms.shabal256(), __ -> {});
		application = mockApplication();
	}

	private static Application mockApplication() throws TransactionRejectedException, TimeoutException, InterruptedException, ApplicationException, UnknownGroupIdException {
		var application = mock(Application.class);
		when(application.checkPrologExtra(any())).thenReturn(true);
		doNothing().when(application).checkTransaction(any());
		when(application.getInitialStateId()).thenReturn(stateId);
		doNothing().when(application).deliverTransaction(anyInt(), any());
		when(application.endBlock(anyInt(), any())).thenReturn(stateId);

		return application;
	}

	@AfterAll
	public static void afterAll() throws IOException, InterruptedException {
		plot.close();
	}

	private static class TestNode extends AbstractLocalNode {
		private TestNode(Path dir) throws InvalidKeyException, NoSuchAlgorithmException, SignatureException, IOException, InterruptedException, AlreadyInitializedException, NodeException, TimeoutException {
			this(dir, application);
		}

		private TestNode(Path dir, Application application) throws InvalidKeyException, NoSuchAlgorithmException, SignatureException, IOException, InterruptedException, AlreadyInitializedException, NodeException, TimeoutException {
			super(mkConfig(dir), nodeKeys, application, false);
		}

		private TestNode(LocalNodeConfig config) throws InvalidKeyException, SignatureException, IOException, InterruptedException, AlreadyInitializedException, NodeException, TimeoutException {
			super(config, nodeKeys, application, false);
		}
	}

	@Test
	@DisplayName("if an added non-genesis block is too much in the future, verification rejects it")
	public void blockTooMuchInTheFutureGetsRejected(@TempDir Path dir) throws NoSuchAlgorithmException, VerificationException, InvalidKeyException, SignatureException, IOException, InterruptedException, AlreadyInitializedException, TimeoutException, NodeException {
		var config = LocalNodeConfigBuilders.defaults()
				.setDir(dir)
				.setBlockMaxTimeInTheFuture(1000)
				.setChainId("octopus")
				.build();

		try (var node = new TestNode(config)) {
			var blockchain = node.getBlockchain();
			var hashingForDeadlines = config.getHashingForDeadlines();
			var hashingForBlocks = config.getHashingForBlocks();
			var hashingForGenerations = config.getHashingForGenerations();
			var generationSignature = new byte[hashingForGenerations.length()];
			for (int pos = 0; pos < generationSignature.length; pos++)
				generationSignature[pos] = (byte) (42 + pos);
			var description = BlockDescriptions.genesis(LocalDateTime.now(ZoneId.of("UTC")), BigInteger.valueOf(config.getInitialAcceleration()), config.getSignatureForBlocks(), nodeKeys.getPublic());
			var genesis = Blocks.genesis(description, stateId, nodePrivateKey);
			var value = new byte[hashingForDeadlines.length()];
			for (int pos = 0; pos < value.length; pos++)
				value[pos] = (byte) pos;
			var deadline = Deadlines.of(prolog, 13, value, Challenges.of(11, generationSignature, hashingForDeadlines, hashingForGenerations), plotPrivateKey);
			byte[] previous = genesis.getHash(hashingForBlocks);
			var block = Blocks.of(BlockDescriptions.of(1, BigInteger.TEN, config.getBlockMaxTimeInTheFuture() + 1000, 1100L, BigInteger.valueOf(13011973), deadline, previous, hashingForBlocks), Stream.empty(), stateId, nodePrivateKey);

			assertTrue(blockchain.add(genesis));
			VerificationException e = assertThrows(VerificationException.class, () -> blockchain.add(block));
			assertTrue(e.getMessage().startsWith("Too much in the future"));
			assertBlockchainIsJustGenesis(blockchain, genesis, config);
		}
	}

	@Test
	@DisplayName("if an added genesis block is too much in the future, verification rejects it")
	public void genesisTooMuchInTheFutureGetsRejected(@TempDir Path dir) throws NoSuchAlgorithmException, VerificationException, InvalidKeyException, SignatureException, IOException, InterruptedException, AlreadyInitializedException, NodeException, TimeoutException {
		var config = LocalNodeConfigBuilders.defaults()
			.setDir(dir)
			.setChainId("octopus")
			.setBlockMaxTimeInTheFuture(1000)
			.build();

		try (var node = new TestNode(config)) {
			var blockchain = node.getBlockchain();
			var description1 = BlockDescriptions.genesis(LocalDateTime.now(ZoneId.of("UTC")), BigInteger.valueOf(config.getInitialAcceleration()), config.getSignatureForBlocks(), nodeKeys.getPublic());
			var genesis1 = Blocks.genesis(description1, stateId, nodePrivateKey);
			var description2 = BlockDescriptions.genesis(genesis1.getStartDateTimeUTC().plus(config.getBlockMaxTimeInTheFuture() + 1000, ChronoUnit.MINUTES), BigInteger.valueOf(config.getInitialAcceleration()), config.getSignatureForBlocks(), nodeKeys.getPublic());
			var genesis2 = Blocks.genesis(description2, stateId, nodePrivateKey);

			assertTrue(blockchain.add(genesis1));
			VerificationException e = assertThrows(VerificationException.class, () -> blockchain.add(genesis2));
			assertTrue(e.getMessage().startsWith("Too much in the future"));
			assertBlockchainIsJustGenesis(blockchain, genesis1, config);
		}
	}

	@Test
	@DisplayName("if an added non-genesis block has inconsistent height, verification rejects it")
	public void blockHeightMismatchGetsRejected(@TempDir Path dir) throws NoSuchAlgorithmException, VerificationException, IOException, InvalidKeyException, SignatureException, InterruptedException, AlreadyInitializedException, NodeException, TimeoutException {
		try (var node = new TestNode(dir, application)) {
			var blockchain = node.getBlockchain();
			var config = node.getConfig();
			var hashingForDeadlines = config.getHashingForDeadlines();
			var description = BlockDescriptions.genesis(LocalDateTime.now(ZoneId.of("UTC")), BigInteger.valueOf(config.getInitialAcceleration()), config.getSignatureForBlocks(), nodeKeys.getPublic());
			var genesis = Blocks.genesis(description, stateId, nodePrivateKey);
			var deadline = plot.getSmallestDeadline(description.getNextChallenge(config.getHashingForGenerations(), hashingForDeadlines), plotPrivateKey);
			var expected = genesis.getNextBlockDescription(deadline, config);
			// we replace the expected block hash
			var actual = BlockDescriptions.of(expected.getHeight() + 1, expected.getPower(), expected.getTotalWaitingTime(), expected.getWeightedWaitingTime(), expected.getAcceleration(),
				expected.getDeadline(), expected.getHashOfPreviousBlock(), expected.getHashingForBlocks());
			var block = Blocks.of(actual, Stream.empty(), stateId, nodePrivateKey);

			assertTrue(blockchain.add(genesis));
			VerificationException e = assertThrows(VerificationException.class, () -> blockchain.add(block));
			assertTrue(e.getMessage().startsWith("Height mismatch"));
			assertBlockchainIsJustGenesis(blockchain, genesis, config);
		}
	}

	@Test
	@DisplayName("if an added non-genesis block has inconsistent acceleration, verification rejects it")
	public void accelerationMismatchGetsRejected(@TempDir Path dir) throws NoSuchAlgorithmException, VerificationException, IOException, InvalidKeyException, SignatureException, InterruptedException, AlreadyInitializedException, NodeException, TimeoutException {
		try (var node = new TestNode(dir)) {
			var blockchain = node.getBlockchain();
			var config = node.getConfig();
			var hashingForDeadlines = config.getHashingForDeadlines();
			var description = BlockDescriptions.genesis(LocalDateTime.now(ZoneId.of("UTC")), BigInteger.valueOf(config.getInitialAcceleration()), config.getSignatureForBlocks(), nodeKeys.getPublic());
			var genesis = Blocks.genesis(description, stateId, nodePrivateKey);
			var deadline = plot.getSmallestDeadline(description.getNextChallenge(config.getHashingForGenerations(), hashingForDeadlines), plotPrivateKey);
			var expected = genesis.getNextBlockDescription(deadline, config);
			// we replace the expected acceleration
			var actual = BlockDescriptions.of(expected.getHeight(), expected.getPower(), expected.getTotalWaitingTime(), expected.getWeightedWaitingTime(), expected.getAcceleration().add(BigInteger.ONE),
					expected.getDeadline(), expected.getHashOfPreviousBlock(), expected.getHashingForBlocks());
			var block = Blocks.of(actual, Stream.empty(), stateId, nodePrivateKey);

			assertTrue(blockchain.add(genesis));
			VerificationException e = assertThrows(VerificationException.class, () -> blockchain.add(block));
			assertTrue(e.getMessage().startsWith("Acceleration mismatch"));
			assertBlockchainIsJustGenesis(blockchain, genesis, config);
		}
	}

	@Test
	@DisplayName("if an added non-genesis block has inconsistent power, verification rejects it")
	public void powerMismatchGetsRejected(@TempDir Path dir) throws NoSuchAlgorithmException, VerificationException, IOException, InvalidKeyException, SignatureException, InterruptedException, AlreadyInitializedException, NodeException, TimeoutException {
		try (var node = new TestNode(dir)) {
			var blockchain = node.getBlockchain();
			var config = node.getConfig();
			var hashingForDeadlines = config.getHashingForDeadlines();
			var description = BlockDescriptions.genesis(LocalDateTime.now(ZoneId.of("UTC")), BigInteger.valueOf(config.getInitialAcceleration()), config.getSignatureForBlocks(), nodeKeys.getPublic());
			var genesis = Blocks.genesis(description, stateId, nodePrivateKey);
			var deadline = plot.getSmallestDeadline(description.getNextChallenge(config.getHashingForGenerations(), hashingForDeadlines), plotPrivateKey);
			var expected = genesis.getNextBlockDescription(deadline, config);
			// we replace the expected power
			var actual = BlockDescriptions.of(expected.getHeight(), expected.getPower().add(BigInteger.ONE), expected.getTotalWaitingTime(), expected.getWeightedWaitingTime(), expected.getAcceleration(),
				expected.getDeadline(), expected.getHashOfPreviousBlock(), expected.getHashingForBlocks());
			var block = Blocks.of(actual, Stream.empty(), stateId, nodePrivateKey);

			assertTrue(blockchain.add(genesis));
			VerificationException e = assertThrows(VerificationException.class, () -> blockchain.add(block));
			assertTrue(e.getMessage().startsWith("Power mismatch"));
			assertBlockchainIsJustGenesis(blockchain, genesis, config);
		}
	}

	@Test
	@DisplayName("if an added non-genesis block has inconsistent total waiting time, verification rejects it")
	public void totalWaitingTimeMismatchGetsRejected(@TempDir Path dir) throws NoSuchAlgorithmException, VerificationException, IOException, InvalidKeyException, SignatureException, InterruptedException, AlreadyInitializedException, NodeException, TimeoutException {
		try (var node = new TestNode(dir)) {
			var blockchain = node.getBlockchain();
			var config = node.getConfig();
			var hashingForDeadlines = config.getHashingForDeadlines();
			var description = BlockDescriptions.genesis(LocalDateTime.now(ZoneId.of("UTC")), BigInteger.valueOf(config.getInitialAcceleration()), config.getSignatureForBlocks(), nodeKeys.getPublic());
			var genesis = Blocks.genesis(description, stateId, nodePrivateKey);
			var deadline = plot.getSmallestDeadline(description.getNextChallenge(config.getHashingForGenerations(), hashingForDeadlines), plotPrivateKey);
			var expected = genesis.getNextBlockDescription(deadline, config);
			// we replace the expected total waiting time
			var actual = BlockDescriptions.of(expected.getHeight(), expected.getPower(), expected.getTotalWaitingTime() + 1, expected.getWeightedWaitingTime(), expected.getAcceleration(),
				expected.getDeadline(), expected.getHashOfPreviousBlock(), expected.getHashingForBlocks());
			var block = Blocks.of(actual, Stream.empty(), stateId, nodePrivateKey);

			assertTrue(blockchain.add(genesis));
			VerificationException e = assertThrows(VerificationException.class, () -> blockchain.add(block));
			assertTrue(e.getMessage().startsWith("Total waiting time mismatch"));
			assertBlockchainIsJustGenesis(blockchain, genesis, config);
		}
	}

	@Test
	@DisplayName("if a non-genesis block is signed with the wrong key, its creation fails")
	public void wrongBlockSignatureGetsRejected(@TempDir Path dir) throws NoSuchAlgorithmException, VerificationException, IOException, InvalidKeyException, SignatureException, InterruptedException {
		var config = mkConfig(dir);
		var description = BlockDescriptions.genesis(LocalDateTime.now(ZoneId.of("UTC")), BigInteger.valueOf(config.getInitialAcceleration()), config.getSignatureForBlocks(), nodeKeys.getPublic());
		var genesis = Blocks.genesis(description, stateId, nodePrivateKey);
		var deadline = plot.getSmallestDeadline(description.getNextChallenge(config.getHashingForGenerations(), config.getHashingForDeadlines()), plotPrivateKey);
		var expected = genesis.getNextBlockDescription(deadline, config);
		// we replace the correct signature with a fake one
		var newKeys = config.getSignatureForBlocks().getKeyPair();
		IllegalArgumentException e = assertThrows(IllegalArgumentException.class, () -> Blocks.of(expected, Stream.empty(), stateId, newKeys.getPrivate()));
		assertTrue(e.getMessage().startsWith("The block's signature is invalid"));
	}

	@Test
	@DisplayName("if a block contains a repeated transaction, its creation fails")
	public void repeatedTransactionGetsRejected(@TempDir Path dir) throws NoSuchAlgorithmException, VerificationException, IOException, InvalidKeyException, SignatureException, InterruptedException {
		var config = mkConfig(dir);
		var description = BlockDescriptions.genesis(LocalDateTime.now(ZoneId.of("UTC")), BigInteger.valueOf(config.getInitialAcceleration()), config.getSignatureForBlocks(), nodeKeys.getPublic());
		var genesis = Blocks.genesis(description, stateId, nodePrivateKey);
		var deadline = plot.getSmallestDeadline(description.getNextChallenge(config.getHashingForGenerations(), config.getHashingForDeadlines()), plotPrivateKey);
		var expected = genesis.getNextBlockDescription(deadline, config);
		var tx1 = Transactions.of(new byte[] { 1, 2, 3, 4 });
		var tx2 = Transactions.of(new byte[] { 13, 1, 19, 73 });
		var tx3 = Transactions.of(new byte[] { 4, 50 });
		// we place transaction tx2 twice
		IllegalArgumentException e = assertThrows(IllegalArgumentException.class, () -> Blocks.of(expected, Stream.of(tx1, tx2, tx3, tx2), stateId, nodePrivateKey));
		assertTrue(e.getMessage().startsWith("Repeated transaction"));
	}

	@Test
	@DisplayName("if an added non-genesis block has inconsistent deadline's scoop number, verification rejects it")
	public void deadlineScoopNumberMismatchGetsRejected(@TempDir Path dir) throws NoSuchAlgorithmException, VerificationException, IOException, InvalidKeyException, SignatureException, InterruptedException, AlreadyInitializedException, NodeException, TimeoutException {
		try (var node = new TestNode(dir)) {
			var blockchain = node.getBlockchain();
			var config = node.getConfig();
			var hashingForDeadlines = config.getHashingForDeadlines();
			var description = BlockDescriptions.genesis(LocalDateTime.now(ZoneId.of("UTC")), BigInteger.valueOf(config.getInitialAcceleration()), config.getSignatureForBlocks(), nodeKeys.getPublic());
			var genesis = Blocks.genesis(description, stateId, nodePrivateKey);
			var deadline = plot.getSmallestDeadline(description.getNextChallenge(config.getHashingForGenerations(), hashingForDeadlines), plotPrivateKey);
			var expected = genesis.getNextBlockDescription(deadline, config);
			// we replace the expected deadline
			var challenge = deadline.getChallenge();
			var modifiedDeadline = Deadlines.of(deadline.getProlog(), deadline.getProgressive(), deadline.getValue(),
				Challenges.of((challenge.getScoopNumber() + 1) % Challenge.SCOOPS_PER_NONCE, challenge.getGenerationSignature(), challenge.getHashingForDeadlines(), challenge.getHashingForGenerations()), plotPrivateKey);
			var actual = BlockDescriptions.of(expected.getHeight(), expected.getPower(), expected.getTotalWaitingTime(), expected.getWeightedWaitingTime(), expected.getAcceleration(),
				modifiedDeadline, expected.getHashOfPreviousBlock(), expected.getHashingForBlocks());
			var block = Blocks.of(actual, Stream.empty(), stateId, nodePrivateKey);

			assertTrue(blockchain.add(genesis));
			VerificationException e = assertThrows(VerificationException.class, () -> blockchain.add(block));
			assertTrue(e.getMessage().startsWith("Deadline mismatch: scoop number mismatch"));
			assertBlockchainIsJustGenesis(blockchain, genesis, config);
		}
	}

	@Test
	@DisplayName("if an added non-genesis block contains a transaction already in blockchain, verification rejects it")
	public void transactionAlreadyInBlockchainGetsRejected(@TempDir Path dir) throws NoSuchAlgorithmException, VerificationException, IOException, InvalidKeyException, SignatureException, InterruptedException, AlreadyInitializedException, NodeException, TimeoutException {
		var tx1 = Transactions.of(new byte[] { 1, 2, 3, 4 });
		var tx2 = Transactions.of(new byte[] { 13, 1, 19, 73 });
		var tx3 = Transactions.of(new byte[] { 4, 50 });

		try (var node = new TestNode(dir)) {
			var blockchain = node.getBlockchain();
			var config = node.getConfig();
			var hashingForDeadlines = config.getHashingForDeadlines();
			var description = BlockDescriptions.genesis(LocalDateTime.now(ZoneId.of("UTC")), BigInteger.valueOf(config.getInitialAcceleration()), config.getSignatureForBlocks(), nodeKeys.getPublic());
			var genesis = Blocks.genesis(description, stateId, nodePrivateKey);
			var deadline = plot.getSmallestDeadline(description.getNextChallenge(config.getHashingForGenerations(), hashingForDeadlines), plotPrivateKey);
			var expected = genesis.getNextBlockDescription(deadline, config);
			var block1 = Blocks.of(expected, Stream.of(tx2), stateId, nodePrivateKey);
			deadline = plot.getSmallestDeadline(expected.getNextChallenge(config.getHashingForGenerations(), hashingForDeadlines), plotPrivateKey);
			expected = block1.getNextBlockDescription(deadline, config);
			var block2 = Blocks.of(expected, Stream.of(tx1, tx2, tx3), stateId, nodePrivateKey);

			assertTrue(blockchain.add(genesis));
			assertTrue(blockchain.add(block1));
			VerificationException e = assertThrows(VerificationException.class, () -> blockchain.add(block2));
			assertTrue(e.getMessage().startsWith("Repeated transaction"));
			assertBlockchainIsJustTwoBlocks(blockchain, genesis, block1, config);
		}
	}

	@Test
	@DisplayName("if the transactions table of an added non-genesis block is too big, verification rejects it")
	public void transactionsTooBigForNonGenesisGetsRejected(@TempDir Path dir) throws NoSuchAlgorithmException, VerificationException, IOException, InvalidKeyException, SignatureException, InterruptedException, AlreadyInitializedException, NodeException, TimeoutException {
		var tx1 = Transactions.of(new byte[] { 1, 2, 3, 4 });
		var tx2 = Transactions.of(new byte[] { 13, 1, 19, 73 });
		var tx3 = Transactions.of(new byte[] { 4, 50 });
		var config = mkConfig(dir).toBuilder().setMaxBlockSize(6).build();

		try (var node = new TestNode(config)) {
			var blockchain = node.getBlockchain();
			var hashingForDeadlines = config.getHashingForDeadlines();
			var description = BlockDescriptions.genesis(LocalDateTime.now(ZoneId.of("UTC")), BigInteger.valueOf(config.getInitialAcceleration()), config.getSignatureForBlocks(), nodeKeys.getPublic());
			var genesis = Blocks.genesis(description, stateId, nodePrivateKey);
			var deadline = plot.getSmallestDeadline(description.getNextChallenge(config.getHashingForGenerations(), hashingForDeadlines), plotPrivateKey);
			var expected = genesis.getNextBlockDescription(deadline, config);
			var block = Blocks.of(expected, Stream.of(tx1, tx2, tx3), stateId, nodePrivateKey);

			assertTrue(blockchain.add(genesis));
			VerificationException e = assertThrows(VerificationException.class, () -> blockchain.add(block));
			assertTrue(e.getMessage().startsWith("The table of transactions is too big"));
			assertBlockchainIsJustGenesis(blockchain, genesis, config);
		}
	}

	@Test
	@DisplayName("if a block contains a transaction that does not pass the application check, verification rejects it")
	public void transactionNotCheckedGetsRejected(@TempDir Path dir) throws NoSuchAlgorithmException, VerificationException, IOException, InvalidKeyException, SignatureException, InterruptedException, AlreadyInitializedException, TransactionRejectedException, TimeoutException, NodeException, ApplicationException, UnknownGroupIdException {
		var app = mockApplication();
		var tx1 = Transactions.of(new byte[] { 1, 2, 3, 4 });
		var tx2 = Transactions.of(new byte[] { 13, 1, 19, 73 });
		doThrow(new TransactionRejectedException("tx2 rejected")).when(app).checkTransaction(eq(tx2));
		var tx3 = Transactions.of(new byte[] { 4, 50 });

		try (var node = new TestNode(dir, app)) {
			var blockchain = node.getBlockchain();
			var config = node.getConfig();
			var hashingForDeadlines = config.getHashingForDeadlines();
			var description = BlockDescriptions.genesis(LocalDateTime.now(ZoneId.of("UTC")), BigInteger.valueOf(config.getInitialAcceleration()), config.getSignatureForBlocks(), nodeKeys.getPublic());
			var genesis = Blocks.genesis(description, stateId, nodePrivateKey);
			var deadline = plot.getSmallestDeadline(description.getNextChallenge(config.getHashingForGenerations(), hashingForDeadlines), plotPrivateKey);
			var expected = genesis.getNextBlockDescription(deadline, config);
			var block1 = Blocks.of(expected, Stream.of(tx1, tx3), stateId, nodePrivateKey);
			deadline = plot.getSmallestDeadline(expected.getNextChallenge(config.getHashingForGenerations(), hashingForDeadlines), plotPrivateKey);
			expected = block1.getNextBlockDescription(deadline, config);
			var block2 = Blocks.of(expected, Stream.of(tx2), stateId, nodePrivateKey);

			assertTrue(blockchain.add(genesis));
			assertTrue(blockchain.add(block1));
			VerificationException e = assertThrows(VerificationException.class, () -> blockchain.add(block2));
			assertTrue(e.getMessage().startsWith("Failed check of transaction"));
			assertBlockchainIsJustTwoBlocks(blockchain, genesis, block1, config);
		}
	}

	@Test
	@DisplayName("if a block contains a transaction that does not pass the delivery check, verification rejects it")
	public void transactionNotDeliveredGetsRejected(@TempDir Path dir) throws NoSuchAlgorithmException, VerificationException, IOException, InvalidKeyException, SignatureException, InterruptedException, AlreadyInitializedException, TransactionRejectedException, TimeoutException, NodeException, ApplicationException, UnknownGroupIdException {
		var app = mockApplication();
		var tx1 = Transactions.of(new byte[] { 1, 2, 3, 4 });
		var tx2 = Transactions.of(new byte[] { 13, 1, 19, 73 });
		doThrow(new TransactionRejectedException("tx2 rejected")).when(app).deliverTransaction(anyInt(), eq(tx2));
		var tx3 = Transactions.of(new byte[] { 4, 50 });

		try (var node = new TestNode(dir, app)) {
			var blockchain = node.getBlockchain();
			var config = node.getConfig();
			var hashingForDeadlines = config.getHashingForDeadlines();
			var description = BlockDescriptions.genesis(LocalDateTime.now(ZoneId.of("UTC")), BigInteger.valueOf(config.getInitialAcceleration()), config.getSignatureForBlocks(), nodeKeys.getPublic());
			var genesis = Blocks.genesis(description, stateId, nodePrivateKey);
			var deadline = plot.getSmallestDeadline(description.getNextChallenge(config.getHashingForGenerations(), hashingForDeadlines), plotPrivateKey);
			var expected = genesis.getNextBlockDescription(deadline, config);
			var block1 = Blocks.of(expected, Stream.of(tx1, tx3), stateId, nodePrivateKey);
			deadline = plot.getSmallestDeadline(expected.getNextChallenge(config.getHashingForGenerations(), hashingForDeadlines), plotPrivateKey);
			expected = block1.getNextBlockDescription(deadline, config);
			var block2 = Blocks.of(expected, Stream.of(tx2), stateId, nodePrivateKey);

			assertTrue(blockchain.add(genesis));
			assertTrue(blockchain.add(block1));
			VerificationException e = assertThrows(VerificationException.class, () -> blockchain.add(block2));
			assertTrue(e.getMessage().startsWith("Failed delivery of transaction"));
			assertBlockchainIsJustTwoBlocks(blockchain, genesis, block1, config);
		}
	}

	@Test
	@DisplayName("if a block contains a final state hash that does not match that resulting at the end of its transactions, verification rejects it")
	public void finalStateMismatchGetsRejected(@TempDir Path dir) throws NoSuchAlgorithmException, VerificationException, IOException, InvalidKeyException, SignatureException, InterruptedException, AlreadyInitializedException, TransactionRejectedException, TimeoutException, NodeException, ApplicationException, UnknownGroupIdException {
		var app = mockApplication();
		var tx = Transactions.of(new byte[] { 13, 1, 19, 73 });
		when(app.endBlock(anyInt(), any())).thenReturn(new byte[] { 42, 17, 13 });

		try (var node = new TestNode(dir, app)) {
			var blockchain = node.getBlockchain();
			var config = node.getConfig();
			var hashingForDeadlines = config.getHashingForDeadlines();
			var description = BlockDescriptions.genesis(LocalDateTime.now(ZoneId.of("UTC")), BigInteger.valueOf(config.getInitialAcceleration()), config.getSignatureForBlocks(), nodeKeys.getPublic());
			var genesis = Blocks.genesis(description, stateId, nodePrivateKey);
			var deadline = plot.getSmallestDeadline(description.getNextChallenge(config.getHashingForGenerations(), hashingForDeadlines), plotPrivateKey);
			var expected = genesis.getNextBlockDescription(deadline, config);
			var block = Blocks.of(expected, Stream.of(tx), stateId, nodePrivateKey);

			assertTrue(blockchain.add(genesis));
			VerificationException e = assertThrows(VerificationException.class, () -> blockchain.add(block));
			assertTrue(e.getMessage().startsWith("Final state mismatch"));
			assertBlockchainIsJustGenesis(blockchain, genesis, config);
		}
	}

	@Test
	@DisplayName("if an added non-genesis block has inconsistent deadline's generation signature, verification rejects it")
	public void deadlineGenerationSignatureMismatchGetsRejected(@TempDir Path dir) throws NoSuchAlgorithmException, VerificationException, IOException, InvalidKeyException, SignatureException, InterruptedException, AlreadyInitializedException, NodeException, TimeoutException {
		try (var node = new TestNode(dir)) {
			var blockchain = node.getBlockchain();
			var config = node.getConfig();
			var hashingForDeadlines = config.getHashingForDeadlines();
			var description = BlockDescriptions.genesis(LocalDateTime.now(ZoneId.of("UTC")), BigInteger.valueOf(config.getInitialAcceleration()), config.getSignatureForBlocks(), nodeKeys.getPublic());
			var genesis = Blocks.genesis(description, stateId, nodePrivateKey);
			var deadline = plot.getSmallestDeadline(description.getNextChallenge(config.getHashingForGenerations(), hashingForDeadlines), plotPrivateKey);
			var expected = genesis.getNextBlockDescription(deadline, config);

			// we replace the expected deadline
			var challenge = deadline.getChallenge();
			var modifiedGenerationSignature = challenge.getGenerationSignature();
			// blocks' deadlines have a non-empty generation signature array
			modifiedGenerationSignature[0]++;
			var modifiedDeadline = Deadlines.of(deadline.getProlog(), deadline.getProgressive(), deadline.getValue(), Challenges.of(challenge.getScoopNumber(), modifiedGenerationSignature, challenge.getHashingForDeadlines(), challenge.getHashingForGenerations()), plotPrivateKey);
			var actual = BlockDescriptions.of(expected.getHeight(), expected.getPower(), expected.getTotalWaitingTime(), expected.getWeightedWaitingTime(), expected.getAcceleration(),
				modifiedDeadline, expected.getHashOfPreviousBlock(), expected.getHashingForBlocks());
			var block = Blocks.of(actual, Stream.empty(), stateId, nodePrivateKey);

			assertTrue(blockchain.add(genesis));
			VerificationException e = assertThrows(VerificationException.class, () -> blockchain.add(block));
			assertTrue(e.getMessage().startsWith("Deadline mismatch: generation signature mismatch"));
			assertBlockchainIsJustGenesis(blockchain, genesis, config);
		}
	}

	@Test
	@DisplayName("if an added non-genesis block has inconsistent deadline's hashing algorithm, verification rejects it")
	public void deadlineHashingMismatchGetsRejected(@TempDir Path dir) throws NoSuchAlgorithmException, VerificationException, IOException, InvalidKeyException, SignatureException, InterruptedException, AlreadyInitializedException, NodeException, TimeoutException {
		try (var node = new TestNode(dir)) {
			var blockchain = node.getBlockchain();
			var config = node.getConfig();
			var hashingForDeadlines = config.getHashingForDeadlines();
			var description = BlockDescriptions.genesis(LocalDateTime.now(ZoneId.of("UTC")), BigInteger.valueOf(config.getInitialAcceleration()), config.getSignatureForBlocks(), nodeKeys.getPublic());
			var genesis = Blocks.genesis(description, stateId, nodePrivateKey);
			var deadline = plot.getSmallestDeadline(description.getNextChallenge(config.getHashingForGenerations(), hashingForDeadlines), plotPrivateKey);
			var expected = genesis.getNextBlockDescription(deadline, config);

			// we replace the expected deadline
			var sha256 = HashingAlgorithms.sha256();
			var challenge = deadline.getChallenge();
			var otherAlgorithm = challenge.getHashingForDeadlines().equals(sha256) ? HashingAlgorithms.shabal256() : sha256;
			var modifiedDeadline = Deadlines.of(deadline.getProlog(), deadline.getProgressive(), deadline.getValue(), Challenges.of(challenge.getScoopNumber(), challenge.getGenerationSignature(), otherAlgorithm, challenge.getHashingForGenerations()), plotPrivateKey);
			var actual = BlockDescriptions.of(expected.getHeight(), expected.getPower(), expected.getTotalWaitingTime(), expected.getWeightedWaitingTime(), expected.getAcceleration(),
				modifiedDeadline, expected.getHashOfPreviousBlock(), expected.getHashingForBlocks());
			var block = Blocks.of(actual, Stream.empty(), stateId, nodePrivateKey);

			assertTrue(blockchain.add(genesis));
			VerificationException e = assertThrows(VerificationException.class, () -> blockchain.add(block));
			assertTrue(e.getMessage().startsWith("Deadline mismatch: hashing algorithm for deadlines mismatch"));
			assertBlockchainIsJustGenesis(blockchain, genesis, config);
		}
	}

	@Test
	@DisplayName("if an added non-genesis block has the wrong deadline's prolog chain identifier, verification rejects it")
	public void deadlinePrologChainIdMismatchGetsRejected(@TempDir Path dir) throws NoSuchAlgorithmException, VerificationException, IOException, InvalidKeyException, SignatureException, InterruptedException, AlreadyInitializedException, NodeException, TimeoutException {
		try (var node = new TestNode(dir)) {
			var blockchain = node.getBlockchain();
			var config = node.getConfig();
			var hashingForDeadlines = config.getHashingForDeadlines();
			var description = BlockDescriptions.genesis(LocalDateTime.now(ZoneId.of("UTC")), BigInteger.valueOf(config.getInitialAcceleration()), config.getSignatureForBlocks(), nodeKeys.getPublic());
			var genesis = Blocks.genesis(description, stateId, nodePrivateKey);
			var deadline = plot.getSmallestDeadline(description.getNextChallenge(config.getHashingForGenerations(), hashingForDeadlines), plotPrivateKey);
			var expected = genesis.getNextBlockDescription(deadline, config);

			// we create a different prolog
			var modifiedProlog = Prologs.of(prolog.getChainId() + "+", prolog.getSignatureForBlocks(), prolog.getPublicKeyForSigningBlocks(),
					prolog.getSignatureForDeadlines(), prolog.getPublicKeyForSigningDeadlines(), prolog.getExtra());
			var modifiedDeadline = Deadlines.of(modifiedProlog, deadline.getProgressive(), deadline.getValue(), deadline.getChallenge(), plotPrivateKey);
			var actual = BlockDescriptions.of(expected.getHeight(), expected.getPower(), expected.getTotalWaitingTime(), expected.getWeightedWaitingTime(), expected.getAcceleration(),
					modifiedDeadline, expected.getHashOfPreviousBlock(), expected.getHashingForBlocks());
			var block = Blocks.of(actual, Stream.empty(), stateId, nodePrivateKey);

			assertTrue(blockchain.add(genesis));
			VerificationException e = assertThrows(VerificationException.class, () -> blockchain.add(block));
			assertTrue(e.getMessage().startsWith("Deadline prolog's chainId mismatch"));
			assertBlockchainIsJustGenesis(blockchain, genesis, config);
		}
	}

	@Test
	@DisplayName("if an added non-genesis block has the wrong blocks' signature algorithm, verification rejects it")
	public void deadlinePrologBlocksSignatureMismatchGetsRejected(@TempDir Path dir) throws NoSuchAlgorithmException, VerificationException, IOException, InvalidKeyException, SignatureException, InterruptedException, AlreadyInitializedException, NodeException, TimeoutException {
		try (var node = new TestNode(dir)) {
			var blockchain = node.getBlockchain();
			var config = node.getConfig();
			var hashingForDeadlines = config.getHashingForDeadlines();
			var description = BlockDescriptions.genesis(LocalDateTime.now(ZoneId.of("UTC")), BigInteger.valueOf(config.getInitialAcceleration()), config.getSignatureForBlocks(), nodeKeys.getPublic());
			var genesis = Blocks.genesis(description, stateId, nodePrivateKey);
			var deadline = plot.getSmallestDeadline(description.getNextChallenge(config.getHashingForGenerations(), hashingForDeadlines), plotPrivateKey);
			var expected = genesis.getNextBlockDescription(deadline, config);

			// we create a different prolog
			var oldSignature = prolog.getSignatureForBlocks();
			var ed25519 = SignatureAlgorithms.ed25519();
			var sha256dsa = SignatureAlgorithms.sha256dsa();
			var newSignature = oldSignature.equals(ed25519) ? sha256dsa : ed25519;
			var newKeyPair = newSignature.getKeyPair();
			var modifiedProlog = Prologs.of(prolog.getChainId(), newSignature, newKeyPair.getPublic(),
					prolog.getSignatureForDeadlines(), prolog.getPublicKeyForSigningDeadlines(), prolog.getExtra());
			var modifiedDeadline = Deadlines.of(modifiedProlog, deadline.getProgressive(), deadline.getValue(), deadline.getChallenge(), plotPrivateKey);
			var actual = BlockDescriptions.of(expected.getHeight(), expected.getPower(), expected.getTotalWaitingTime(), expected.getWeightedWaitingTime(), expected.getAcceleration(),
					modifiedDeadline, expected.getHashOfPreviousBlock(), expected.getHashingForBlocks());
			var block = Blocks.of(actual, Stream.empty(), stateId, newKeyPair.getPrivate());

			assertTrue(blockchain.add(genesis));
			VerificationException e = assertThrows(VerificationException.class, () -> blockchain.add(block));
			assertTrue(e.getMessage().startsWith("Deadline prolog's signature algorithm for blocks mismatch"));
			assertBlockchainIsJustGenesis(blockchain, genesis, config);
		}
	}

	@Test
	@DisplayName("if an added non-genesis block has the wrong deadlines' signature algorithm, verification rejects it")
	public void deadlinePrologDeadlinesSignatureMismatchGetsRejected(@TempDir Path dir) throws NoSuchAlgorithmException, VerificationException, IOException, InvalidKeyException, SignatureException, InterruptedException, AlreadyInitializedException, NodeException, TimeoutException {
		try (var node = new TestNode(dir)) {
			var blockchain = node.getBlockchain();
			var config = node.getConfig();
			var hashingForDeadlines = config.getHashingForDeadlines();
			var description = BlockDescriptions.genesis(LocalDateTime.now(ZoneId.of("UTC")), BigInteger.valueOf(config.getInitialAcceleration()), config.getSignatureForBlocks(), nodeKeys.getPublic());
			var genesis = Blocks.genesis(description, stateId, nodePrivateKey);
			var deadline = plot.getSmallestDeadline(description.getNextChallenge(config.getHashingForGenerations(), hashingForDeadlines), plotPrivateKey);
			var expected = genesis.getNextBlockDescription(deadline, config);

			// we create a different prolog
			var oldSignature = prolog.getSignatureForDeadlines();
			var ed25519 = SignatureAlgorithms.ed25519();
			var sha256dsa = SignatureAlgorithms.sha256dsa();
			var newSignature = oldSignature.equals(ed25519) ? sha256dsa : ed25519;
			var newKeyPair = newSignature.getKeyPair();
			var modifiedProlog = Prologs.of(prolog.getChainId(), prolog.getSignatureForBlocks(), prolog.getPublicKeyForSigningBlocks(),
					newSignature, newKeyPair.getPublic(), prolog.getExtra());
			var modifiedDeadline = Deadlines.of(modifiedProlog, deadline.getProgressive(), deadline.getValue(), deadline.getChallenge(), newKeyPair.getPrivate());
			var actual = BlockDescriptions.of(expected.getHeight(), expected.getPower(), expected.getTotalWaitingTime(), expected.getWeightedWaitingTime(), expected.getAcceleration(),
					modifiedDeadline, expected.getHashOfPreviousBlock(), expected.getHashingForBlocks());
			var block = Blocks.of(actual, Stream.empty(), stateId, nodePrivateKey);

			assertTrue(blockchain.add(genesis));
			VerificationException e = assertThrows(VerificationException.class, () -> blockchain.add(block));
			assertTrue(e.getMessage().startsWith("Deadline prolog's signature algorithm for deadlines mismatch"));
			assertBlockchainIsJustGenesis(blockchain, genesis, config);
		}
	}

	@Test
	@DisplayName("if an added non-genesis block has a wrong deadline's prolog extra, verification rejects it")
	public void deadlineInvalidPrologExtraGetsRejected(@TempDir Path dir) throws NoSuchAlgorithmException, VerificationException, IOException, InvalidKeyException, SignatureException, InterruptedException, AlreadyInitializedException, TransactionRejectedException, TimeoutException, NodeException, ApplicationException, UnknownGroupIdException {
		var application = mockApplication();
		when(application.checkPrologExtra(any())).thenReturn(false);

		try (var node = new TestNode(dir, application)) {
			var blockchain = node.getBlockchain();
			var config = node.getConfig();
			var hashingForDeadlines = config.getHashingForDeadlines();
			var description = BlockDescriptions.genesis(LocalDateTime.now(ZoneId.of("UTC")), BigInteger.valueOf(config.getInitialAcceleration()), config.getSignatureForBlocks(), nodeKeys.getPublic());
			var genesis = Blocks.genesis(description, stateId, nodePrivateKey);
			var deadline = plot.getSmallestDeadline(description.getNextChallenge(config.getHashingForGenerations(), hashingForDeadlines), plotPrivateKey);
			var expectedDescription = genesis.getNextBlockDescription(deadline, config);
			var expected = Blocks.of(BlockDescriptions.of(expectedDescription.getHeight(), expectedDescription.getPower(), expectedDescription.getTotalWaitingTime(),
					expectedDescription.getWeightedWaitingTime(), expectedDescription.getAcceleration(), expectedDescription.getDeadline(), expectedDescription.getHashOfPreviousBlock(), expectedDescription.getHashingForBlocks()),
					Stream.empty(), stateId, nodePrivateKey);

			assertTrue(blockchain.add(genesis));
			VerificationException e = assertThrows(VerificationException.class, () -> blockchain.add(expected));
			assertTrue(e.getMessage().startsWith("Invalid deadline prolog's extra"));
			assertBlockchainIsJustGenesis(blockchain, genesis, config);
		}
	}

	@Test
	@DisplayName("if an added non-genesis block has an invalid deadline progressive, verification rejects it")
	public void invalidDeadlineProgressiveGetsRejected(@TempDir Path dir) throws NoSuchAlgorithmException, VerificationException, IOException, InvalidKeyException, SignatureException, InterruptedException, AlreadyInitializedException, NodeException, TimeoutException {
		try (var node = new TestNode(dir)) {
			var blockchain = node.getBlockchain();
			var config = node.getConfig();
			var hashingForDeadlines = config.getHashingForDeadlines();
			var description = BlockDescriptions.genesis(LocalDateTime.now(ZoneId.of("UTC")), BigInteger.valueOf(config.getInitialAcceleration()), config.getSignatureForBlocks(), nodeKeys.getPublic());
			var genesis = Blocks.genesis(description, stateId, nodePrivateKey);
			var deadline = plot.getSmallestDeadline(description.getNextChallenge(config.getHashingForGenerations(), hashingForDeadlines), plotPrivateKey);
			var expected = genesis.getNextBlockDescription(deadline, config);

			// we make the deadline invalid by changing its progressive
			var modifiedDeadline = Deadlines.of(deadline.getProlog(), deadline.getProgressive() + 1, deadline.getValue(), deadline.getChallenge(), plotPrivateKey);
			var actual = BlockDescriptions.of(expected.getHeight(), expected.getPower(), expected.getTotalWaitingTime(), expected.getWeightedWaitingTime(), expected.getAcceleration(),
					modifiedDeadline, expected.getHashOfPreviousBlock(), expected.getHashingForBlocks());
			var block = Blocks.of(actual, Stream.empty(), stateId, nodePrivateKey);

			assertTrue(blockchain.add(genesis));
			VerificationException e = assertThrows(VerificationException.class, () -> blockchain.add(block));
			assertTrue(e.getMessage().startsWith("Invalid deadline"));
			assertBlockchainIsJustGenesis(blockchain, genesis, config);
		}
	}

	@Test
	@DisplayName("if an added non-genesis block has an invalid deadline value, verification rejects it")
	public void invalidDeadlineValueGetsRejected(@TempDir Path dir) throws NoSuchAlgorithmException, VerificationException, IOException, InvalidKeyException, SignatureException, InterruptedException, AlreadyInitializedException, NodeException, TimeoutException {
		try (var node = new TestNode(dir, application)) {
			var blockchain = node.getBlockchain();
			var config = node.getConfig();
			var hashingForDeadlines = config.getHashingForDeadlines();
			var description = BlockDescriptions.genesis(LocalDateTime.now(ZoneId.of("UTC")), BigInteger.valueOf(config.getInitialAcceleration()), config.getSignatureForBlocks(), nodeKeys.getPublic());
			var genesis = Blocks.genesis(description, stateId, nodePrivateKey);
			var deadline = plot.getSmallestDeadline(description.getNextChallenge(config.getHashingForGenerations(), hashingForDeadlines), plotPrivateKey);
			var expected = genesis.getNextBlockDescription(deadline, config);

			// we make the deadline invalid by changing its value (it is not empty since it is a hash)
			var value = deadline.getValue();
			value[0]++;
			var modifiedDeadline = Deadlines.of(deadline.getProlog(), deadline.getProgressive(), value, deadline.getChallenge(), plotPrivateKey);
			var actual = BlockDescriptions.of(expected.getHeight(), expected.getPower(), expected.getTotalWaitingTime(), expected.getWeightedWaitingTime(), expected.getAcceleration(),
					modifiedDeadline, expected.getHashOfPreviousBlock(), expected.getHashingForBlocks());
			var block = Blocks.of(actual, Stream.empty(), stateId, nodePrivateKey);

			assertTrue(blockchain.add(genesis));
			VerificationException e = assertThrows(VerificationException.class, () -> blockchain.add(block));
			assertTrue(e.getMessage().startsWith("Invalid deadline"));
			assertBlockchainIsJustGenesis(blockchain, genesis, config);
		}
	}

	private static LocalNodeConfig mkConfig(Path dir) throws NoSuchAlgorithmException {
		return LocalNodeConfigBuilders.defaults()
				.setDir(dir)
				// we effectively disable the time check
				.setBlockMaxTimeInTheFuture(Long.MAX_VALUE)
				.setChainId("octopus")
				.build();
	}

	private static void assertBlockchainIsJustGenesis(Blockchain blockchain, GenesisBlock genesis, LocalNodeConfig config) throws NodeException {
		assertEquals(genesis, blockchain.getGenesis().get());
		assertEquals(genesis, blockchain.getHead().get());
		byte[][] chain = blockchain.getChain(0, 100).toArray(byte[][]::new);
		assertEquals(1, chain.length);
		assertArrayEquals(chain[0], genesis.getHash(config.getHashingForBlocks()));
	}

	private static void assertBlockchainIsJustTwoBlocks(Blockchain blockchain, GenesisBlock genesis, NonGenesisBlock block, LocalNodeConfig config) throws NodeException {
		assertEquals(genesis, blockchain.getGenesis().get());
		assertEquals(block, blockchain.getHead().get());
		byte[][] chain = blockchain.getChain(0, 100).toArray(byte[][]::new);
		assertEquals(2, chain.length);
		assertArrayEquals(chain[0], genesis.getHash(config.getHashingForBlocks()));
		assertArrayEquals(chain[1], block.getHash(config.getHashingForBlocks()));
	}
}