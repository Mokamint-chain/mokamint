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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.doNothing;
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
import java.util.Set;
import java.util.concurrent.TimeoutException;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import io.hotmoka.testing.AbstractLoggedTests;
import io.mokamint.application.api.Application;
import io.mokamint.application.api.ApplicationException;
import io.mokamint.node.BlockDescriptions;
import io.mokamint.node.Blocks;
import io.mokamint.node.Transactions;
import io.mokamint.node.api.Block;
import io.mokamint.node.api.ClosedNodeException;
import io.mokamint.node.api.DatabaseException;
import io.mokamint.node.api.NodeException;
import io.mokamint.node.api.NonGenesisBlock;
import io.mokamint.node.api.RejectedTransactionException;
import io.mokamint.node.api.Transaction;
import io.mokamint.node.local.AlreadyInitializedException;
import io.mokamint.node.local.LocalNodeConfigBuilders;
import io.mokamint.node.local.api.LocalNodeConfig;
import io.mokamint.node.local.internal.ClosedDatabaseException;
import io.mokamint.node.local.internal.LocalNodeImpl;
import io.mokamint.node.local.internal.VerificationException;
import io.mokamint.nonce.Deadlines;
import io.mokamint.nonce.Prologs;
import io.mokamint.nonce.api.DeadlineValidityCheckException;
import io.mokamint.nonce.api.Prolog;
import io.mokamint.plotter.Plots;
import io.mokamint.plotter.api.Plot;

public class MempoolTests extends AbstractLoggedTests {

	/**
	 * The prolog of the plot files.
	 */
	private static Prolog PROLOG;

	/**
	 * The keys of the node.
	 */
	private static KeyPair nodeKeys;

	/**
	 * The hash of the state of the test application.
	 */
	private final static byte[] stateHash = new byte[] { 1, 1, 1, 1, 1, 1 };

	/**
	 * The application running in the node.
	 */
	private static Application application;

	/**
	 * The private key used to sign the blocks.
	 */
	private static PrivateKey privateKey;

	/**
	 * The private key used to sign the deadlines.
	 */
	private static PrivateKey plotPrivateKey;

	/**
	 * The plots used for creating the deadlines.
	 */
	private static Plot plot1;
	private static Plot plot2;

	@BeforeAll
	public static void beforeAll(@TempDir Path plotDir) throws IOException, NoSuchAlgorithmException, InvalidKeyException, RejectedTransactionException, TimeoutException, InterruptedException, ApplicationException {
		var config = LocalNodeConfigBuilders.defaults().build();
		var hashing = config.getHashingForDeadlines();
		var signature = config.getSignatureForBlocks();
		nodeKeys = signature.getKeyPair();
		var plotKeyPair = signature.getKeyPair();

		privateKey = nodeKeys.getPrivate();
		plotPrivateKey = plotKeyPair.getPrivate();

		PROLOG = Prologs.of("octopus", signature, nodeKeys.getPublic(), signature, plotKeyPair.getPublic(), new byte[0]);
		plot1 = Plots.create(plotDir.resolve("plot1.plot"), PROLOG, 65536L, 50L, hashing, __ -> {});
		plot2 = Plots.create(plotDir.resolve("plot2.plot"), PROLOG, 10000L, 100L, hashing, __ -> {});

		application = mock(Application.class);
		when(application.checkPrologExtra(any())).thenReturn(true);
		doNothing().when(application).checkTransaction(any());
		when(application.getPriority(any())).thenReturn(13L);
		when(application.getInitialStateId()).thenReturn(stateHash);
		doNothing().when(application).deliverTransaction(any(), anyInt());
		when(application.endBlock(anyInt(), any())).thenReturn(stateHash);
	}

	@AfterAll
	public static void afterAll() throws IOException, InterruptedException {
		plot1.close();
		plot2.close();
	}

	private static class TestNode extends LocalNodeImpl {
		private TestNode(Path dir) throws InvalidKeyException, NoSuchAlgorithmException, SignatureException, DatabaseException, IOException, InterruptedException, AlreadyInitializedException, TimeoutException {
			this(dir, application);
		}

		private TestNode(Path dir, Application application) throws InvalidKeyException, NoSuchAlgorithmException, SignatureException, DatabaseException, IOException, InterruptedException, AlreadyInitializedException, TimeoutException {
			super(mkConfig(dir), nodeKeys, application, false);
		}

		private TestNode(LocalNodeConfig config) throws InvalidKeyException, NoSuchAlgorithmException, SignatureException, DatabaseException, IOException, InterruptedException, AlreadyInitializedException, TimeoutException {
			super(config, nodeKeys, application, false);
		}
	}

	private static LocalNodeConfig mkConfig(Path dir) throws NoSuchAlgorithmException {
		return LocalNodeConfigBuilders.defaults()
			.setDir(dir)
			.setChainId("octopus")
			// we effectively disable the time check
			.setBlockMaxTimeInTheFuture(Long.MAX_VALUE)
			.build();
	}

	@Test
	@DisplayName("if a block with unknown previous is added, the mempool is unchanged")
	public void ifBlockWithUnknownPreviousIsAddedThenMempoolIsNotChanged(@TempDir Path dir) throws NoSuchAlgorithmException, DatabaseException, VerificationException, ClosedDatabaseException, InvalidKeyException, SignatureException, InterruptedException, IOException, AlreadyInitializedException, RejectedTransactionException, ClosedNodeException, TimeoutException, NodeException, DeadlineValidityCheckException {
		try (var node = new TestNode(dir)) {
			var blockchain = node.getBlockchain();
			var config = node.getConfig();
			var hashingForDeadlines = config.getHashingForDeadlines();
			var description = BlockDescriptions.genesis(LocalDateTime.now(ZoneId.of("UTC")), BigInteger.valueOf(config.getInitialAcceleration()), config.getSignatureForBlocks(), nodeKeys.getPublic());
			var genesis = Blocks.genesis(description, stateHash, nodeKeys.getPrivate());
			var value = new byte[hashingForDeadlines.length()];
			for (int pos = 0; pos < value.length; pos++)
				value[pos] = (byte) pos;
			var deadline = Deadlines.of(PROLOG, 13, value, 11, new byte[] { 90, 91, 92 }, hashingForDeadlines, plotPrivateKey);
			var unknownPrevious = new byte[] { 1, 2, 3, 4, 5, 6};
			var block = Blocks.of(BlockDescriptions.of(13, BigInteger.TEN, 1234L, 1100L, BigInteger.valueOf(13011973), deadline, unknownPrevious), Stream.empty(), stateHash, privateKey);

			assertTrue(blockchain.add(genesis));

			var transaction1 = Transactions.of(new byte[] { 1, 2, 3, 4 });
			var transaction2 = Transactions.of(new byte[] { 2, 2, 3, 4 });
			var transaction3 = Transactions.of(new byte[] { 3, 2, 3, 4 });
			var expectedEntries = Set.of(node.add(transaction1), node.add(transaction2), node.add(transaction3));

			assertFalse(blockchain.add(block));

			var actualEntries = node.getMempoolPortion(0, 10).getEntries().collect(Collectors.toSet());
			assertEquals(3, actualEntries.size());
			assertEquals(expectedEntries, actualEntries);
			assertEquals(3, node.getMempoolInfo().getSize());
		}
	}

	@Test
	@DisplayName("if a block is added to the head of the chain, its transactions are removed from the mempool")
	public void transactionsInNonGenesisRemovedAfterAddition(@TempDir Path dir) throws NoSuchAlgorithmException, DatabaseException, VerificationException, ClosedDatabaseException, IOException, InvalidKeyException, SignatureException, InterruptedException, AlreadyInitializedException, RejectedTransactionException, ClosedNodeException, TimeoutException, NodeException, DeadlineValidityCheckException {
		try (var node = new TestNode(dir)) {
			var blockchain = node.getBlockchain();
			var config = node.getConfig();
			var description = BlockDescriptions.genesis(LocalDateTime.now(ZoneId.of("UTC")), BigInteger.valueOf(config.getInitialAcceleration()), config.getSignatureForBlocks(), nodeKeys.getPublic());
			var genesis = Blocks.genesis(description, stateHash, nodeKeys.getPrivate());

			var transaction1 = Transactions.of(new byte[] { 1, 2, 3, 4 });
			var transaction2 = Transactions.of(new byte[] { 2, 2, 3, 4 });
			var transaction3 = Transactions.of(new byte[] { 3, 2, 3, 4 });
			var expectedEntries = Set.of(node.add(transaction1), node.add(transaction3));
			node.add(transaction2);

			var block = computeNextBlock(genesis, Stream.of(transaction2), config);

			assertTrue(blockchain.add(genesis));
			assertTrue(blockchain.add(block));

			var actualEntries = node.getMempoolPortion(0, 10).getEntries().collect(Collectors.toSet());
			assertEquals(2, actualEntries.size());
			assertEquals(expectedEntries, actualEntries);
			assertEquals(2, node.getMempoolInfo().getSize());
		}
	}

	@Test
	@DisplayName("if a block is added to the chain but head has more power, the mempool remains unchanged")
	public void ifBlockAddedToChainButHeadBetterThenMempoolIsNotChanged(@TempDir Path dir) throws NoSuchAlgorithmException, DatabaseException, VerificationException, ClosedDatabaseException, IOException, InvalidKeyException, SignatureException, InterruptedException, AlreadyInitializedException, RejectedTransactionException, ClosedNodeException, TimeoutException, NodeException, DeadlineValidityCheckException {
		try (var node = new TestNode(dir)) {
			var blockchain = node.getBlockchain();
			var config = node.getConfig();
			var description = BlockDescriptions.genesis(LocalDateTime.now(ZoneId.of("UTC")), BigInteger.valueOf(config.getInitialAcceleration()), config.getSignatureForBlocks(), nodeKeys.getPublic());
			var genesis = Blocks.genesis(description, stateHash, nodeKeys.getPrivate());

			var transaction1 = Transactions.of(new byte[] { 1, 2, 3, 4 });
			var transaction2 = Transactions.of(new byte[] { 2, 2, 3, 4 });
			var transaction3 = Transactions.of(new byte[] { 3, 2, 3, 4 });
			var expectedEntries = Set.of(node.add(transaction1), node.add(transaction2), node.add(transaction3));

			var block1 = computeNextBlock(genesis, config, plot1);
			var added = computeNextBlock(genesis, Stream.of(transaction1, transaction2, transaction3), config, plot2);
			if (block1.getDescription().getPower().compareTo(added.getDescription().getPower()) < 0) {
				// we invert the blocks, so that block1 has always at least the power of added
				// note: the transactions in the block to not contribute to the deadline and hence to the power
				block1 = computeNextBlock(genesis, config, plot2);
				added = computeNextBlock(genesis, Stream.of(transaction1, transaction2, transaction3), config, plot1);
			}

			var block2 = computeNextBlock(block1, config);
			var block3 = computeNextBlock(block2, config);

			assertTrue(blockchain.add(genesis));
			assertTrue(blockchain.add(block1));
			assertTrue(blockchain.add(block2));
			assertTrue(blockchain.add(block3));
			assertTrue(blockchain.add(added));

			var actualEntries = node.getMempoolPortion(0, 10).getEntries().collect(Collectors.toSet());
			assertEquals(3, actualEntries.size());
			assertEquals(expectedEntries, actualEntries);
			assertEquals(3, node.getMempoolInfo().getSize());
		}
	}

	@Test
	@DisplayName("if a chain with more power than the current chain is added, then the mempool is rebased")
	public void ifMorePowerfulChainIsAddedThenMempoolRebased(@TempDir Path dir) throws NoSuchAlgorithmException, DatabaseException, VerificationException, ClosedDatabaseException, IOException, InvalidKeyException, SignatureException, InterruptedException, AlreadyInitializedException, RejectedTransactionException, ClosedNodeException, TimeoutException, NodeException, DeadlineValidityCheckException {
		try (var node = new TestNode(dir)) {
			var blockchain = node.getBlockchain();
			var config = node.getConfig();
			var description = BlockDescriptions.genesis(LocalDateTime.now(ZoneId.of("UTC")), BigInteger.valueOf(config.getInitialAcceleration()), config.getSignatureForBlocks(), nodeKeys.getPublic());

			var transaction0 = Transactions.of(new byte[] { 1 , 56, 17, 90, 110, 1, 28 });
			var transaction1 = Transactions.of(new byte[] { 1, 2, 3, 4 });
			var transaction2 = Transactions.of(new byte[] { 2, 2, 3, 4 });
			var transaction3 = Transactions.of(new byte[] { 3, 2, 3, 4 });
			var entry0 = node.add(transaction0);
			var entry1 = node.add(transaction1);
			var entry2 = node.add(transaction2);
			var entry3 = node.add(transaction3);

			var genesis = Blocks.genesis(description, stateHash, nodeKeys.getPrivate());
			var blockBase = computeNextBlock(genesis, Stream.of(transaction0), config, plot1);
			var block1 = computeNextBlock(blockBase, Stream.of(transaction2), config, plot1);
			var block0 = computeNextBlock(blockBase, Stream.of(transaction1), config, plot2);
			if (block1.getDescription().getPower().compareTo(block0.getDescription().getPower()) < 0) {
				// we invert the blocks, so that block1 has always at least the power of added
				// note: the transactions in the block to not contribute to the deadline and hence to the power
				block1 = computeNextBlock(blockBase, Stream.of(transaction2), config, plot2);
				block0 = computeNextBlock(blockBase, Stream.of(transaction1), config, plot1);
			}

			var block2 = computeNextBlock(block1, config);
			var block3 = computeNextBlock(block2, Stream.of(transaction3), config);

			// at this stage, the mempool contains all four transactions
			var expectedEntries = Set.of(entry0, entry1, entry2, entry3);
			var actualEntries = node.getMempoolPortion(0, 10).getEntries().collect(Collectors.toSet());
			assertEquals(4, actualEntries.size());
			assertEquals(expectedEntries, actualEntries);
			assertEquals(4, node.getMempoolInfo().getSize());
			
			assertTrue(blockchain.add(genesis));

			// at this stage, the mempool contains all four transactions
			actualEntries = node.getMempoolPortion(0, 10).getEntries().collect(Collectors.toSet());
			assertEquals(4, actualEntries.size());
			assertEquals(expectedEntries, actualEntries);
			assertEquals(4, node.getMempoolInfo().getSize());

			assertTrue(blockchain.add(blockBase));

			// now, the mempool contains only three transactions, since transaction0 is in blockBase
			expectedEntries = Set.of(entry1, entry2, entry3);
			actualEntries = node.getMempoolPortion(0, 10).getEntries().collect(Collectors.toSet());
			assertEquals(3, actualEntries.size());
			assertEquals(expectedEntries, actualEntries);
			assertEquals(3, node.getMempoolInfo().getSize());

			assertTrue(blockchain.add(block0));

			// at this stage, the mempool does not contain transaction1 anymore
			expectedEntries = Set.of(entry2, entry3);
			actualEntries = node.getMempoolPortion(0, 10).getEntries().collect(Collectors.toSet());
			assertEquals(2, actualEntries.size());
			assertEquals(expectedEntries, actualEntries);
			assertEquals(2, node.getMempoolInfo().getSize());

			// we add an orphan block3 (no previous in database)
			assertFalse(blockchain.add(block3));

			// block3 contains transaction3 but it is not removed from the mempool
			actualEntries = node.getMempoolPortion(0, 10).getEntries().collect(Collectors.toSet());
			assertEquals(2, actualEntries.size());
			assertEquals(expectedEntries, actualEntries);
			assertEquals(2, node.getMempoolInfo().getSize());

			// we add another orphan block2 (no previous in database) without transactions
			assertFalse(blockchain.add(block2));

			// the mempool did not change
			actualEntries = node.getMempoolPortion(0, 10).getEntries().collect(Collectors.toSet());
			assertEquals(2, actualEntries.size());
			assertEquals(expectedEntries, actualEntries);
			assertEquals(2, node.getMempoolInfo().getSize());

			// we add a block after blockBase, that creates a better chain of length 4
			assertTrue(blockchain.add(block1));

			// the more powerful chain is the current chain now:
			// transaction1 ends back into the mempool, while transaction2 and transaction3 gets removed from the mempool
			expectedEntries = Set.of(entry1);
			actualEntries = node.getMempoolPortion(0, 10).getEntries().collect(Collectors.toSet());
			assertEquals(1, actualEntries.size());
			assertEquals(expectedEntries, actualEntries);
			assertEquals(1, node.getMempoolInfo().getSize());
		}
	}

	@Test
	@DisplayName("if the more powerful chain is added with genesis at the root, then all its transactions are removed from the mempool")
	public void ifMorePowerfulChainAddedWithGenesisAtTheRootThenAllTransactionsRemovedFromMempool(@TempDir Path dir) throws NoSuchAlgorithmException, DatabaseException, VerificationException, ClosedDatabaseException, IOException, InvalidKeyException, SignatureException, InterruptedException, AlreadyInitializedException, RejectedTransactionException, ClosedNodeException, TimeoutException, NodeException, DeadlineValidityCheckException {
		try (var node = new TestNode(dir)) {
			var blockchain = node.getBlockchain();
			var config = node.getConfig();
			var description = BlockDescriptions.genesis(LocalDateTime.now(ZoneId.of("UTC")), BigInteger.valueOf(config.getInitialAcceleration()), config.getSignatureForBlocks(), nodeKeys.getPublic());

			var transaction0 = Transactions.of(new byte[] { 1 , 56, 17, 90, 110, 1, 28 });
			var transaction1 = Transactions.of(new byte[] { 1, 2, 3, 4 });
			var transaction2 = Transactions.of(new byte[] { 2, 2, 3, 4 });
			var transaction3 = Transactions.of(new byte[] { 3, 2, 3, 4 });
			var entry0 = node.add(transaction0);
			var entry1 = node.add(transaction1);
			var entry2 = node.add(transaction2);
			var entry3 = node.add(transaction3);

			var genesis = Blocks.genesis(description, stateHash, nodeKeys.getPrivate());
			var blockBase = computeNextBlock(genesis, Stream.of(transaction0), config, plot1);
			var block1 = computeNextBlock(blockBase, Stream.of(transaction1), config);
			var block2 = computeNextBlock(block1, Stream.of(transaction2), config);
			var block3 = computeNextBlock(block2, Stream.of(transaction3), config);

			// at this stage, the mempool contains all four transactions
			var expectedEntries = Set.of(entry0, entry1, entry2, entry3);
			var actualEntries = node.getMempoolPortion(0, 10).getEntries().collect(Collectors.toSet());
			assertEquals(4, actualEntries.size());
			assertEquals(expectedEntries, actualEntries);
			assertEquals(4, node.getMempoolInfo().getSize());

			assertFalse(blockchain.add(block3));

			// the mempool did not change
			actualEntries = node.getMempoolPortion(0, 10).getEntries().collect(Collectors.toSet());
			assertEquals(4, actualEntries.size());
			assertEquals(expectedEntries, actualEntries);
			assertEquals(4, node.getMempoolInfo().getSize());

			assertFalse(blockchain.add(block2));

			// the mempool did not change
			actualEntries = node.getMempoolPortion(0, 10).getEntries().collect(Collectors.toSet());
			assertEquals(4, actualEntries.size());
			assertEquals(expectedEntries, actualEntries);
			assertEquals(4, node.getMempoolInfo().getSize());

			assertFalse(blockchain.add(block1));

			// the mempool did not change
			actualEntries = node.getMempoolPortion(0, 10).getEntries().collect(Collectors.toSet());
			assertEquals(4, actualEntries.size());
			assertEquals(expectedEntries, actualEntries);
			assertEquals(4, node.getMempoolInfo().getSize());

			assertFalse(blockchain.add(blockBase));

			// the mempool did not change
			actualEntries = node.getMempoolPortion(0, 10).getEntries().collect(Collectors.toSet());
			assertEquals(4, actualEntries.size());
			assertEquals(expectedEntries, actualEntries);
			assertEquals(4, node.getMempoolInfo().getSize());

			assertTrue(blockchain.add(genesis));

			// the mempool is empty now
			actualEntries = node.getMempoolPortion(0, 10).getEntries().collect(Collectors.toSet());
			assertTrue(actualEntries.isEmpty());
			assertEquals(0, node.getMempoolInfo().getSize());
		}
	}

	private NonGenesisBlock computeNextBlock(Block previous, LocalNodeConfig config) throws IOException, InvalidKeyException, SignatureException, InterruptedException {
		return computeNextBlock(previous, config, plot1);
	}

	private NonGenesisBlock computeNextBlock(Block previous, Stream<Transaction> transactions, LocalNodeConfig config) throws IOException, InvalidKeyException, SignatureException, InterruptedException {
		return computeNextBlock(previous, transactions, config, plot1);
	}

	private NonGenesisBlock computeNextBlock(Block previous, LocalNodeConfig config, Plot plot) throws IOException, InvalidKeyException, SignatureException, InterruptedException {
		return computeNextBlock(previous, Stream.empty(), config, plot);
	}

	private NonGenesisBlock computeNextBlock(Block previous, Stream<Transaction> transactions, LocalNodeConfig config, Plot plot) throws IOException, InvalidKeyException, SignatureException, InterruptedException {
		var nextDeadlineDescription = previous.getNextDeadlineDescription(config.getHashingForGenerations(), config.getHashingForDeadlines());
		var deadline = plot.getSmallestDeadline(nextDeadlineDescription, plotPrivateKey);
		var description = previous.getNextBlockDescription(deadline, config.getTargetBlockCreationTime(), config.getHashingForBlocks(), config.getHashingForDeadlines());
		return Blocks.of(description, transactions, stateHash, privateKey);
	}
}