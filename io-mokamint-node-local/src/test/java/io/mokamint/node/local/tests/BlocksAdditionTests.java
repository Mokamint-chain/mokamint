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
import static org.mockito.AdditionalAnswers.returnsFirstArg;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.math.BigInteger;
import java.nio.file.Path;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.function.Function;
import java.util.stream.Stream;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import io.hotmoka.crypto.HashingAlgorithms;
import io.hotmoka.crypto.SignatureAlgorithms;
import io.hotmoka.crypto.api.HashingAlgorithm;
import io.hotmoka.testing.AbstractLoggedTests;
import io.mokamint.application.api.Application;
import io.mokamint.node.Blocks;
import io.mokamint.node.api.Block;
import io.mokamint.node.api.DatabaseException;
import io.mokamint.node.api.NonGenesisBlock;
import io.mokamint.node.local.LocalNodeConfig;
import io.mokamint.node.local.LocalNodeConfigBuilders;
import io.mokamint.node.local.internal.ClosedDatabaseException;
import io.mokamint.node.local.internal.LocalNodeImpl;
import io.mokamint.node.local.internal.NodeMiners;
import io.mokamint.node.local.internal.NodePeers;
import io.mokamint.node.local.internal.blockchain.Blockchain;
import io.mokamint.node.local.internal.blockchain.VerificationException;
import io.mokamint.nonce.Deadlines;
import io.mokamint.nonce.Prologs;
import io.mokamint.nonce.api.Prolog;
import io.mokamint.plotter.Plots;
import io.mokamint.plotter.api.Plot;

public class BlocksAdditionTests extends AbstractLoggedTests {

	/**
	 * The prolog of the plot files.
	 */
	private static Prolog PROLOG;

	/**
	 * The plots used for creating the deadlines.
	 */
	private static Plot plot1;
	private static Plot plot2;
	private static Plot plot3;

	@BeforeAll
	public static void beforeAll(@TempDir Path plotDir) throws IOException, NoSuchAlgorithmException, InvalidKeyException {
		var hashing = HashingAlgorithms.shabal256(Function.identity());
		var ed25519 = SignatureAlgorithms.ed25519(Function.identity());
		PROLOG = Prologs.of("octopus", ed25519.getKeyPair().getPublic(), ed25519.getKeyPair().getPublic(), new byte[0]);

		plot1 = Plots.create(plotDir.resolve("plot1.plot"), PROLOG, 65536L, 50L, hashing, __ -> {});
		plot2 = Plots.create(plotDir.resolve("plot2.plot"), PROLOG, 10000L, 100L, hashing, __ -> {});
		plot3 = Plots.create(plotDir.resolve("plot3.plot"), PROLOG, 15000L, 256L, hashing, __ -> {});
	}

	@AfterAll
	public static void afterAll() throws IOException {
		plot1.close();
		plot2.close();
		plot3.close();
	}

	private static LocalNodeConfig mkConfig(Path dir) throws NoSuchAlgorithmException {
		return LocalNodeConfigBuilders.defaults()
			.setDir(dir)
			.setChainId("octopus")
			// we effectively disable the time check
			.setBlockMaxTimeInTheFuture(Long.MAX_VALUE)
			.build();
	}

	private static Blockchain mkTestBlockchain(LocalNodeConfig config) throws DatabaseException {
		var peers = mock(NodePeers.class);
		doAnswer(returnsFirstArg())
			.when(peers)
			.asNetworkDateTime(any());

		var application = mock(Application.class);
		when(application.prologExtraIsValid(any())).thenReturn(true);

		var node = mock(LocalNodeImpl.class);
		when(node.getConfig()).thenReturn(config);
		when(node.getApplication()).thenReturn(application);
		when(node.getPeers()).thenReturn(peers);
		var miners = new NodeMiners(node);
		when(node.getMiners()).thenReturn(miners);
		var blockchain = new Blockchain(node);
		when(node.getBlockchain()).thenReturn(blockchain);

		return blockchain;
	}

	@Test
	@DisplayName("the first genesis block added to the database becomes head and genesis of the chain")
	public void firstGenesisBlockBecomesHeadAndGenesis(@TempDir Path dir) throws NoSuchAlgorithmException, DatabaseException, VerificationException, ClosedDatabaseException {
		var config = mkConfig(dir);
		var blockchain = mkTestBlockchain(config);
		var genesis = Blocks.genesis(LocalDateTime.now(ZoneId.of("UTC")), BigInteger.ONE);

		assertTrue(blockchain.add(genesis));
		assertEquals(genesis, blockchain.getGenesis().get());
		assertEquals(genesis, blockchain.getHead().get());
		byte[][] chain = blockchain.getChain(0, 100).toArray(byte[][]::new);
		assertEquals(1, chain.length);
		assertArrayEquals(chain[0], genesis.getHash(config.getHashingForBlocks()));
	}

	@Test
	@DisplayName("if the genesis of the chain is set, a subsequent genesis block is not added")
	public void ifGenesisIsSetNextGenesisBlockIsRejected(@TempDir Path dir) throws NoSuchAlgorithmException, DatabaseException, VerificationException, ClosedDatabaseException {
		var config = mkConfig(dir);
		var blockchain = mkTestBlockchain(config);
		var genesis1 = Blocks.genesis(LocalDateTime.now(ZoneId.of("UTC")), BigInteger.ONE);
		var genesis2 = Blocks.genesis(LocalDateTime.now(ZoneId.of("UTC")).plus(1, ChronoUnit.MILLIS), BigInteger.ONE);

		assertTrue(blockchain.add(genesis1));
		assertFalse(blockchain.add(genesis2));
		assertEquals(genesis1, blockchain.getGenesis().get());
		assertEquals(genesis1, blockchain.getHead().get());
		byte[][] chain = blockchain.getChain(0, 100).toArray(byte[][]::new);
		assertEquals(1, chain.length);
		assertArrayEquals(chain[0], genesis1.getHash(config.getHashingForBlocks()));
	}

	@Test
	@DisplayName("if a block with unknown previous is added, the head of the chain does not change")
	public void ifBlockWithUnknownPreviousIsAddedThenHeadIsNotChanged(@TempDir Path dir) throws NoSuchAlgorithmException, DatabaseException, VerificationException, ClosedDatabaseException, InvalidKeyException {
		var config = mkConfig(dir);
		var blockchain = mkTestBlockchain(config);
		var hashingForDeadlines = config.getHashingForDeadlines();
		var genesis = Blocks.genesis(LocalDateTime.now(ZoneId.of("UTC")), BigInteger.ONE);
		var value = new byte[hashingForDeadlines.length()];
		for (int pos = 0; pos < value.length; pos++)
			value[pos] = (byte) pos;
		var id25519 = SignatureAlgorithms.ed25519(Function.identity());
		var prolog = Prologs.of("octopus", id25519.getKeyPair().getPublic(), id25519.getKeyPair().getPublic(), new byte[0]);
		var deadline = Deadlines.of(prolog, 13, value, 11, new byte[] { 90, 91, 92 }, hashingForDeadlines);
		var unknownPrevious = new byte[] { 1, 2, 3, 4, 5, 6};
		var block = Blocks.of(13, BigInteger.TEN, 1234L, 1100L, BigInteger.valueOf(13011973), deadline, unknownPrevious);

		assertTrue(blockchain.add(genesis));
		assertFalse(blockchain.add(block));
		assertEquals(genesis, blockchain.getGenesis().get());
		assertEquals(genesis, blockchain.getHead().get());
		byte[][] chain = blockchain.getChain(0, 100).toArray(byte[][]::new);
		assertEquals(1, chain.length);
		assertArrayEquals(chain[0], genesis.getHash(config.getHashingForBlocks()));
	}

	@Test
	@DisplayName("if a block is added to the head of the chain, it becomes the head of the chain")
	public void ifBlockAddedToHeadOfChainThenItBecomesHead(@TempDir Path dir) throws NoSuchAlgorithmException, DatabaseException, VerificationException, ClosedDatabaseException, IOException {
		var config = mkConfig(dir);
		var blockchain = mkTestBlockchain(config);
		var genesis = Blocks.genesis(LocalDateTime.now(ZoneId.of("UTC")), BigInteger.ONE);
		var block = computeNextBlock(genesis, config);

		assertTrue(blockchain.add(genesis));
		assertTrue(blockchain.add(block));
		assertEquals(genesis, blockchain.getGenesis().get());
		assertEquals(block, blockchain.getHead().get());
		byte[][] chain = blockchain.getChain(0, 100).toArray(byte[][]::new);
		assertEquals(2, chain.length);
		assertArrayEquals(chain[0], genesis.getHash(config.getHashingForBlocks()));
		assertArrayEquals(chain[1], block.getHash(config.getHashingForBlocks()));
	}

	@Test
	@DisplayName("if a block is added to the chain but head has more power, the head of the chain is not changed")
	public void ifBlockAddedToChainButHeadBetterThenHeadIsNotChanged(@TempDir Path dir) throws NoSuchAlgorithmException, DatabaseException, VerificationException, ClosedDatabaseException, IOException {
		var config = mkConfig(dir);
		var blockchain = mkTestBlockchain(config);
		var genesis = Blocks.genesis(LocalDateTime.now(ZoneId.of("UTC")), BigInteger.ONE);
		var block1 = computeNextBlock(genesis, config, plot1);
		var added = computeNextBlock(genesis, config, plot2);
		if (block1.getPower().compareTo(added.getPower()) < 0) {
			// we invert the blocks, so that block1 has always at least the power of added
			var temp = block1;
			block1 = added;
			added = temp;
		}

		var block2 = computeNextBlock(block1, config);
		var block3 = computeNextBlock(block2, config);

		assertTrue(blockchain.add(genesis));
		assertTrue(blockchain.add(block1));
		assertTrue(blockchain.add(block2));
		assertTrue(blockchain.add(block3));
		assertTrue(blockchain.add(added));
		assertEquals(genesis, blockchain.getGenesis().get());
		assertEquals(block3, blockchain.getHead().get());
		byte[][] chain = blockchain.getChain(0, 100).toArray(byte[][]::new);
		assertEquals(4, chain.length);
		HashingAlgorithm<byte[]> hashingForBlocks = config.getHashingForBlocks();
		assertArrayEquals(chain[0], genesis.getHash(hashingForBlocks));
		assertArrayEquals(chain[1], block1.getHash(hashingForBlocks));
		assertArrayEquals(chain[2], block2.getHash(hashingForBlocks));
		assertArrayEquals(chain[3], block3.getHash(hashingForBlocks));
	}

	@Test
	@DisplayName("if a chain with more power than the current chain is added, then it becomes the current chain")
	public void ifLongerChainIsAddedThenItBecomesTheCurrentChain(@TempDir Path dir) throws NoSuchAlgorithmException, DatabaseException, VerificationException, ClosedDatabaseException, IOException {
		var config = mkConfig(dir);
		var blockchain = mkTestBlockchain(config);
		var genesis = Blocks.genesis(LocalDateTime.now(ZoneId.of("UTC")), BigInteger.ONE);
		var block1 = computeNextBlock(genesis, config, plot1);
		var block0 = computeNextBlock(genesis, config, plot2);
		if (block1.getPower().compareTo(block0.getPower()) < 0) {
			// we invert the blocks, so that block1 has always at least the power of block0
			var temp = block1;
			block1 = block0;
			block0 = temp;
		}

		var block2 = computeNextBlock(block1, config);
		var block3 = computeNextBlock(block2, config);

		assertTrue(blockchain.add(genesis));
		assertTrue(blockchain.add(block0));

		// at this stage, block0 is the head of the current chain, of length 2
		assertEquals(genesis, blockchain.getGenesis().get());
		assertEquals(block0, blockchain.getHead().get());
		byte[][] chain = blockchain.getChain(0, 100).toArray(byte[][]::new);
		assertEquals(2, chain.length);
		HashingAlgorithm<byte[]> hashingForBlocks = config.getHashingForBlocks();
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

	@Test
	@DisplayName("if more children of the head are added, the one with higher power becomes head")
	public void ifMoreChildrenThanHigherPowerBecomesHead(@TempDir Path dir) throws NoSuchAlgorithmException, DatabaseException, VerificationException, ClosedDatabaseException, IOException {
		var config = mkConfig(dir);
		var blockchain = mkTestBlockchain(config);
		var genesis = Blocks.genesis(LocalDateTime.now(ZoneId.of("UTC")), BigInteger.ONE);
		var sorted = Stream.of(computeNextBlock(genesis, config, plot1), computeNextBlock(genesis, config, plot2), computeNextBlock(genesis, config, plot3))
			.sorted(Comparator.comparing(NonGenesisBlock::getPower)).toArray(NonGenesisBlock[]::new);
		var block3 = sorted[0]; // least powerful
		var block1 = sorted[1]; // medium
		var block2 = sorted[2]; // most powerful

		assertTrue(blockchain.add(genesis));
		assertTrue(blockchain.add(block1));

		// at this stage, block1 is the head of the current chain, of length 2
		assertEquals(genesis, blockchain.getGenesis().get());
		assertEquals(block1, blockchain.getHead().get());
		byte[][] chain = blockchain.getChain(0, 100).toArray(byte[][]::new);
		assertEquals(2, chain.length);
		HashingAlgorithm<byte[]> hashingForBlocks = config.getHashingForBlocks();
		assertArrayEquals(chain[0], genesis.getHash(hashingForBlocks));
		assertArrayEquals(chain[1], block1.getHash(hashingForBlocks));

		// we create a chain with more power as the current chain
		assertTrue(blockchain.add(block2));

		// block2 is the new head now
		assertEquals(genesis, blockchain.getGenesis().get());
		assertEquals(block2, blockchain.getHead().get());
		chain = blockchain.getChain(0, 100).toArray(byte[][]::new);
		assertEquals(2, chain.length);
		assertArrayEquals(chain[0], genesis.getHash(hashingForBlocks));
		assertArrayEquals(chain[1], block2.getHash(hashingForBlocks));

		// we create a chain with the same length as the current chain (2 blocks),
		// but less power than the current head
		assertTrue(blockchain.add(block3));

		// block2 is still the head
		assertEquals(genesis, blockchain.getGenesis().get());
		assertEquals(block2, blockchain.getHead().get());
		chain = blockchain.getChain(0, 100).toArray(byte[][]::new);
		assertEquals(2, chain.length);
		assertArrayEquals(chain[0], genesis.getHash(hashingForBlocks));
		assertArrayEquals(chain[1], block2.getHash(hashingForBlocks));
	}

	@Test
	@DisplayName("if the more powerful chain is added with genesis at the root, then it becomes the current chain")
	public void ifMorePowerfulChainAddedWithGenesisAtTheRootThenItBecomesCurrentChain(@TempDir Path dir) throws NoSuchAlgorithmException, DatabaseException, VerificationException, ClosedDatabaseException, IOException {
		var config = mkConfig(dir);
		var blockchain = mkTestBlockchain(config);
		var genesis = Blocks.genesis(LocalDateTime.now(ZoneId.of("UTC")), BigInteger.ONE);
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
		HashingAlgorithm<byte[]> hashingForBlocks = config.getHashingForBlocks();
		assertArrayEquals(chain[0], genesis.getHash(hashingForBlocks));
		assertArrayEquals(chain[1], block1.getHash(hashingForBlocks));
		assertArrayEquals(chain[2], block2.getHash(hashingForBlocks));
		assertArrayEquals(chain[3], block3.getHash(hashingForBlocks));
	}

	private NonGenesisBlock computeNextBlock(Block previous, LocalNodeConfig config) throws IOException {
		return computeNextBlock(previous, config, plot1);
	}

	private NonGenesisBlock computeNextBlock(Block previous, LocalNodeConfig config, Plot plot) throws IOException {
		var nextDeadlineDescription = previous.getNextDeadlineDescription(config.getHashingForGenerations(), config.getHashingForDeadlines());
		var deadline = plot.getSmallestDeadline(nextDeadlineDescription);
		return previous.getNextBlockDescription(deadline, config.getTargetBlockCreationTime(), config.getHashingForBlocks(), config.getHashingForDeadlines());
	}
}