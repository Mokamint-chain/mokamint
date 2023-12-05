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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
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
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import io.hotmoka.crypto.api.HashingAlgorithm;
import io.hotmoka.testing.AbstractLoggedTests;
import io.mokamint.application.api.Application;
import io.mokamint.node.BlockDescriptions;
import io.mokamint.node.Blocks;
import io.mokamint.node.Transactions;
import io.mokamint.node.api.Block;
import io.mokamint.node.api.ClosedNodeException;
import io.mokamint.node.api.DatabaseException;
import io.mokamint.node.api.MempoolEntry;
import io.mokamint.node.api.NonGenesisBlock;
import io.mokamint.node.api.RejectedTransactionException;
import io.mokamint.node.api.Transaction;
import io.mokamint.node.local.AlreadyInitializedException;
import io.mokamint.node.local.LocalNodeConfigBuilders;
import io.mokamint.node.local.api.LocalNodeConfig;
import io.mokamint.node.local.internal.ClosedDatabaseException;
import io.mokamint.node.local.internal.LocalNodeImpl;
import io.mokamint.node.local.internal.blockchain.VerificationException;
import io.mokamint.nonce.Deadlines;
import io.mokamint.nonce.Prologs;
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
	private static Plot plot3;

	@BeforeAll
	public static void beforeAll(@TempDir Path plotDir) throws IOException, NoSuchAlgorithmException, InvalidKeyException, RejectedTransactionException {
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
		plot3 = Plots.create(plotDir.resolve("plot3.plot"), PROLOG, 15000L, 256L, hashing, __ -> {});

		application = mock(Application.class);
		when(application.checkPrologExtra(any())).thenReturn(true);
		when(application.checkTransaction(any())).thenReturn(true);
		when(application.getPriority(any())).thenReturn(13L);
	}

	@AfterAll
	public static void afterAll() throws IOException, InterruptedException {
		plot1.close();
		plot2.close();
		plot3.close();
	}

	private static class TestNode extends LocalNodeImpl {
		private TestNode(Path dir) throws InvalidKeyException, NoSuchAlgorithmException, SignatureException, DatabaseException, IOException, InterruptedException, AlreadyInitializedException {
			this(dir, application);
		}

		private TestNode(Path dir, Application application) throws InvalidKeyException, NoSuchAlgorithmException, SignatureException, DatabaseException, IOException, InterruptedException, AlreadyInitializedException {
			super(mkConfig(dir), nodeKeys, application, false);
		}

		private TestNode(LocalNodeConfig config) throws InvalidKeyException, NoSuchAlgorithmException, SignatureException, DatabaseException, IOException, InterruptedException, AlreadyInitializedException {
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
	@DisplayName("if a genesis block is added, its transactions get removed from the mempool")
	public void transactionsInGenesisRemovedAfterAddition(@TempDir Path dir) throws NoSuchAlgorithmException, DatabaseException, VerificationException, ClosedDatabaseException, InvalidKeyException, SignatureException, InterruptedException, IOException, AlreadyInitializedException, RejectedTransactionException, ClosedNodeException {
		try (var node = new TestNode(dir)) {
			var blockchain = node.getBlockchain();
			var config = node.getConfig();
			var description = BlockDescriptions.genesis(LocalDateTime.now(ZoneId.of("UTC")), BigInteger.valueOf(config.getInitialAcceleration()), config.getSignatureForBlocks(), nodeKeys.getPublic());
			var transaction1 = Transactions.of(new byte[] { 1, 2, 3, 4 });
			var transaction2 = Transactions.of(new byte[] { 2, 2, 3, 4 });
			var transaction3 = Transactions.of(new byte[] { 3, 2, 3, 4 });
			node.add(transaction1);
			var entry3 = node.add(transaction3);
			node.add(transaction2);
			var genesis = Blocks.genesis(description, Stream.of(transaction2, transaction1), nodeKeys.getPrivate());

			assertTrue(blockchain.add(genesis));
			var entries = node.getMempoolPortion(0, 10).getEntries().toArray(MempoolEntry[]::new);
			assertEquals(entries.length, 1);
			assertEquals(entries[0], entry3);
		}
	}

	@Test
	@DisplayName("if a block with unknown previous is added, the mempool is unchanged")
	public void ifBlockWithUnknownPreviousIsAddedThenMempoolIsNotChanged(@TempDir Path dir) throws NoSuchAlgorithmException, DatabaseException, VerificationException, ClosedDatabaseException, InvalidKeyException, SignatureException, InterruptedException, IOException, AlreadyInitializedException, RejectedTransactionException, ClosedNodeException {
		try (var node = new TestNode(dir)) {
			var blockchain = node.getBlockchain();
			var config = node.getConfig();
			var hashingForDeadlines = config.getHashingForDeadlines();
			var description = BlockDescriptions.genesis(LocalDateTime.now(ZoneId.of("UTC")), BigInteger.valueOf(config.getInitialAcceleration()), config.getSignatureForBlocks(), nodeKeys.getPublic());
			var genesis = Blocks.genesis(description, Stream.empty(), nodeKeys.getPrivate());
			var value = new byte[hashingForDeadlines.length()];
			for (int pos = 0; pos < value.length; pos++)
				value[pos] = (byte) pos;
			var deadline = Deadlines.of(PROLOG, 13, value, 11, new byte[] { 90, 91, 92 }, hashingForDeadlines, plotPrivateKey);
			var unknownPrevious = new byte[] { 1, 2, 3, 4, 5, 6};
			var block = Blocks.of(BlockDescriptions.of(13, BigInteger.TEN, 1234L, 1100L, BigInteger.valueOf(13011973), deadline, unknownPrevious), Stream.empty(), privateKey);

			assertTrue(blockchain.add(genesis));

			var transaction1 = Transactions.of(new byte[] { 1, 2, 3, 4 });
			var transaction2 = Transactions.of(new byte[] { 2, 2, 3, 4 });
			var transaction3 = Transactions.of(new byte[] { 3, 2, 3, 4 });
			var expectedEntries = Set.of(node.add(transaction1), node.add(transaction2), node.add(transaction3));

			assertFalse(blockchain.add(block));

			var actualEntries = node.getMempoolPortion(0, 10).getEntries().collect(Collectors.toSet());
			assertEquals(actualEntries.size(), 3);
			assertEquals(expectedEntries, actualEntries);
		}
	}

	@Test
	@DisplayName("if a block is added to the head of the chain, its transactions are removed from the mempool")
	public void transactionsInNonGenesisRemovedAfterAddition(@TempDir Path dir) throws NoSuchAlgorithmException, DatabaseException, VerificationException, ClosedDatabaseException, IOException, InvalidKeyException, SignatureException, InterruptedException, AlreadyInitializedException, RejectedTransactionException, ClosedNodeException {
		try (var node = new TestNode(dir)) {
			var blockchain = node.getBlockchain();
			var config = node.getConfig();
			var description = BlockDescriptions.genesis(LocalDateTime.now(ZoneId.of("UTC")), BigInteger.valueOf(config.getInitialAcceleration()), config.getSignatureForBlocks(), nodeKeys.getPublic());
			var genesis = Blocks.genesis(description, Stream.empty(), nodeKeys.getPrivate());

			var transaction1 = Transactions.of(new byte[] { 1, 2, 3, 4 });
			var transaction2 = Transactions.of(new byte[] { 2, 2, 3, 4 });
			var transaction3 = Transactions.of(new byte[] { 3, 2, 3, 4 });
			var expectedEntries = Set.of(node.add(transaction1), node.add(transaction3));
			node.add(transaction2);

			var block = computeNextBlock(genesis, Stream.of(transaction2), config);

			assertTrue(blockchain.add(genesis));
			assertTrue(blockchain.add(block));

			var actualEntries = node.getMempoolPortion(0, 10).getEntries().collect(Collectors.toSet());
			assertEquals(actualEntries.size(), 2);
			assertEquals(expectedEntries, actualEntries);
		}
	}

	@Test
	@DisplayName("if a block is added to the chain but head has more power, the mempool remains unchanged")
	public void ifBlockAddedToChainButHeadBetterThenMempoolIsNotChanged(@TempDir Path dir) throws NoSuchAlgorithmException, DatabaseException, VerificationException, ClosedDatabaseException, IOException, InvalidKeyException, SignatureException, InterruptedException, AlreadyInitializedException, RejectedTransactionException, ClosedNodeException {
		try (var node = new TestNode(dir)) {
			var blockchain = node.getBlockchain();
			var config = node.getConfig();
			var description = BlockDescriptions.genesis(LocalDateTime.now(ZoneId.of("UTC")), BigInteger.valueOf(config.getInitialAcceleration()), config.getSignatureForBlocks(), nodeKeys.getPublic());
			var genesis = Blocks.genesis(description, Stream.empty(), nodeKeys.getPrivate());

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
			assertEquals(actualEntries.size(), 3);
			assertEquals(expectedEntries, actualEntries);
		}
	}

	@Test
	@DisplayName("if a chain with more power than the current chain is added, then the mempool is rebased")
	public void ifMorePowerfulChainIsAddedThenMempoolRebased(@TempDir Path dir) throws NoSuchAlgorithmException, DatabaseException, VerificationException, ClosedDatabaseException, IOException, InvalidKeyException, SignatureException, InterruptedException, AlreadyInitializedException, RejectedTransactionException, ClosedNodeException {
		try (var node = new TestNode(dir)) {
			var blockchain = node.getBlockchain();
			var config = node.getConfig();
			var description = BlockDescriptions.genesis(LocalDateTime.now(ZoneId.of("UTC")), BigInteger.valueOf(config.getInitialAcceleration()), config.getSignatureForBlocks(), nodeKeys.getPublic());

			var transaction1 = Transactions.of(new byte[] { 1, 2, 3, 4 });
			var transaction2 = Transactions.of(new byte[] { 2, 2, 3, 4 });
			var transaction3 = Transactions.of(new byte[] { 3, 2, 3, 4 });
			var entry1 = node.add(transaction1);
			var entry2 = node.add(transaction2);
			var entry3 = node.add(transaction3);

			var genesis = Blocks.genesis(description, Stream.empty(), nodeKeys.getPrivate());
			var block1 = computeNextBlock(genesis, Stream.of(transaction2), config, plot1);
			var block0 = computeNextBlock(genesis, Stream.of(transaction1), config, plot2);
			if (block1.getDescription().getPower().compareTo(block0.getDescription().getPower()) < 0) {
				// we invert the blocks, so that block1 has always at least the power of added
				// note: the transactions in the block to not contribute to the deadline and hence to the power
				block1 = computeNextBlock(genesis, Stream.of(transaction2), config, plot2);
				block0 = computeNextBlock(genesis, Stream.of(transaction1), config, plot1);
			}

			var block2 = computeNextBlock(block1, config);
			var block3 = computeNextBlock(block2, Stream.of(transaction3), config);

			// at this stage, the mempool contains all three transactions
			var expectedEntries = Set.of(entry1, entry2, entry3);
			var actualEntries = node.getMempoolPortion(0, 10).getEntries().collect(Collectors.toSet());
			assertEquals(actualEntries.size(), 3);
			assertEquals(expectedEntries, actualEntries);
			
			assertTrue(blockchain.add(genesis));
			assertTrue(blockchain.add(block0));

			// at this stage, the mempool does not contain transaction1 anymore
			expectedEntries = Set.of(entry2, entry3);
			actualEntries = node.getMempoolPortion(0, 10).getEntries().collect(Collectors.toSet());
			System.out.println(actualEntries);
			// TODO
			//assertEquals(2, actualEntries.size());
			//assertEquals(expectedEntries, actualEntries);
			
			assertEquals(genesis, blockchain.getGenesis().get());
			assertEquals(block0, blockchain.getHead().get());
			byte[][] chain = blockchain.getChain(0, 100).toArray(byte[][]::new);
			assertEquals(2, chain.length);
			HashingAlgorithm hashingForBlocks = config.getHashingForBlocks();
			assertArrayEquals(chain[0], genesis.getHash(hashingForBlocks));
			assertArrayEquals(chain[1], block0.getHash(hashingForBlocks));

			// we add an orphan (no previous in database)
			assertFalse(blockchain.add(block3));

			// nothing changes
			assertEquals(genesis, blockchain.getGenesis().get());
			assertEquals(block0, blockchain.getHead().get());
			chain = blockchain.getChain(0, 100).toArray(byte[][]::new);
			assertEquals(2, chain.length);
			assertArrayEquals(chain[0], genesis.getHash(hashingForBlocks));
			assertArrayEquals(chain[1], block0.getHash(hashingForBlocks));

			// we add an orphan (no previous in database)
			assertFalse(blockchain.add(block2));

			// nothing changes
			assertEquals(genesis, blockchain.getGenesis().get());
			assertEquals(block0, blockchain.getHead().get());
			chain = blockchain.getChain(0, 100).toArray(byte[][]::new);
			assertEquals(2, chain.length);
			assertArrayEquals(chain[0], genesis.getHash(hashingForBlocks));
			assertArrayEquals(chain[1], block0.getHash(hashingForBlocks));

			// we add a block after the genesis, that creates a better chain of length 4
			assertTrue(blockchain.add(block1));

			// the more powerful chain is the current chain now
			assertEquals(genesis, blockchain.getGenesis().get());
			assertEquals(block3, blockchain.getHead().get());
			chain = blockchain.getChain(0, 100).toArray(byte[][]::new);
			assertEquals(4, chain.length);
			assertArrayEquals(chain[0], genesis.getHash(hashingForBlocks));
			assertArrayEquals(chain[1], block1.getHash(hashingForBlocks));
			assertArrayEquals(chain[2], block2.getHash(hashingForBlocks));
			assertArrayEquals(chain[3], block3.getHash(hashingForBlocks));
		}
	}

	@Test
	@DisplayName("if the more powerful chain is added with genesis at the root, then it becomes the current chain")
	public void ifMorePowerfulChainAddedWithGenesisAtTheRootThenItBecomesCurrentChain(@TempDir Path dir) throws NoSuchAlgorithmException, DatabaseException, VerificationException, ClosedDatabaseException, IOException, InvalidKeyException, SignatureException, InterruptedException, AlreadyInitializedException {
		try (var node = new TestNode(dir)) {
			var blockchain = node.getBlockchain();
			var config = node.getConfig();
			var description = BlockDescriptions.genesis(LocalDateTime.now(ZoneId.of("UTC")), BigInteger.valueOf(config.getInitialAcceleration()), config.getSignatureForBlocks(), nodeKeys.getPublic());
			var genesis = Blocks.genesis(description, Stream.empty(), nodeKeys.getPrivate());
			var block1 = computeNextBlock(genesis, config);
			var block2 = computeNextBlock(block1, config);
			var block3 = computeNextBlock(block2, config);

			assertFalse(blockchain.add(block3));

			// no genesis and no head are set up to now
			assertTrue(blockchain.getGenesis().isEmpty());
			assertTrue(blockchain.getHead().isEmpty());
			byte[][] chain = blockchain.getChain(0, 100).toArray(byte[][]::new);
			assertEquals(0, chain.length);

			assertFalse(blockchain.add(block2));

			// no genesis and no head are set up to now
			assertTrue(blockchain.getGenesis().isEmpty());
			assertTrue(blockchain.getHead().isEmpty());
			chain = blockchain.getChain(0, 100).toArray(byte[][]::new);
			assertEquals(0, chain.length);

			assertFalse(blockchain.add(block1));

			// no genesis and no head are set up to now
			assertTrue(blockchain.getGenesis().isEmpty());
			assertTrue(blockchain.getHead().isEmpty());
			chain = blockchain.getChain(0, 100).toArray(byte[][]::new);
			assertEquals(0, chain.length);

			assertTrue(blockchain.add(genesis));

			// genesis and head are set now
			assertEquals(genesis, blockchain.getGenesis().get());
			assertEquals(block3, blockchain.getHead().get());
			chain = blockchain.getChain(0, 100).toArray(byte[][]::new);
			assertEquals(4, chain.length);
			HashingAlgorithm hashingForBlocks = config.getHashingForBlocks();
			assertArrayEquals(chain[0], genesis.getHash(hashingForBlocks));
			assertArrayEquals(chain[1], block1.getHash(hashingForBlocks));
			assertArrayEquals(chain[2], block2.getHash(hashingForBlocks));
			assertArrayEquals(chain[3], block3.getHash(hashingForBlocks));
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
		return Blocks.of(description, transactions, privateKey);
	}
}