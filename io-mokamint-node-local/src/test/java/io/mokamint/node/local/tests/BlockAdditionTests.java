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
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Path;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.logging.LogManager;
import java.util.stream.Stream;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import io.mokamint.application.api.Application;
import io.mokamint.node.Blocks;
import io.mokamint.node.api.ClosedNodeException;
import io.mokamint.node.api.DatabaseException;
import io.mokamint.node.local.Config;
import io.mokamint.node.local.internal.Database;
import io.mokamint.node.local.internal.LocalNodeImpl;
import io.mokamint.node.local.internal.NodeMiners;
import io.mokamint.node.local.internal.NodePeers;
import io.mokamint.node.local.internal.blockchain.Blockchain;
import io.mokamint.node.local.internal.blockchain.VerificationException;
import io.mokamint.nonce.Deadlines;

public class BlockAdditionTests {

	private static Config mkConfig(Path dir) throws NoSuchAlgorithmException {
		return Config.Builder.defaults()
			.setDir(dir)
			.build();
	}

	private static Blockchain mkTestBlockchain(Config config) throws DatabaseException, NoSuchAlgorithmException {
		var peers = mock(NodePeers.class);
		doAnswer(returnsFirstArg())
			.when(peers)
			.asNetworkDateTime(any());

		var node = mock(LocalNodeImpl.class);
		when(node.getConfig()).thenReturn(config);
		when(node.getApplication()).thenReturn(mock(Application.class));
		when(node.getPeers()).thenReturn(peers);
		Database database = new Database(node);
		when(node.getDatabase()).thenReturn(database);
		var miners = new NodeMiners(node, Stream.empty());
		when(node.getMiners()).thenReturn(miners);
		var blockchain = new Blockchain(node);
		when(node.getBlockchain()).thenReturn(blockchain);

		return blockchain;
	}

	@Test
	@DisplayName("the first genesis block added to the database becomes head and genesis of the chain")
	public void firstGenesisBlockBecomesHeadAndGenesis(@TempDir Path dir) throws NoSuchAlgorithmException, DatabaseException, URISyntaxException, VerificationException {
		var genesis = Blocks.genesis(LocalDateTime.now(ZoneId.of("UTC")), BigInteger.ONE);
		var config = mkConfig(dir);
		var blockchain = mkTestBlockchain(config);

		assertTrue(blockchain.add(genesis));
		assertEquals(genesis, blockchain.getGenesis().get());
		assertEquals(genesis, blockchain.getHead().get());
		byte[][] chain = blockchain.getChain(0, 100).toArray(byte[][]::new);
		assertEquals(1, chain.length);
		assertArrayEquals(chain[0], genesis.getHash(config.hashingForBlocks));
	}

	@Test
	@DisplayName("if the genesis of the chain is set, a subsequent genesis block is not added")
	public void ifGenesisIsSetNextGenesisBlockIsRejected(@TempDir Path dir) throws NoSuchAlgorithmException, DatabaseException, URISyntaxException, VerificationException {
		var genesis1 = Blocks.genesis(LocalDateTime.now(ZoneId.of("UTC")), BigInteger.ONE);
		var genesis2 = Blocks.genesis(LocalDateTime.now(ZoneId.of("UTC")).plus(1, ChronoUnit.MINUTES), BigInteger.ONE);
		var config = mkConfig(dir);
		var blockchain = mkTestBlockchain(config);

		assertTrue(blockchain.add(genesis1));
		assertFalse(blockchain.add(genesis2));
		assertEquals(genesis1, blockchain.getGenesis().get());
		assertEquals(genesis1, blockchain.getHead().get());
		byte[][] chain = blockchain.getChain(0, 100).toArray(byte[][]::new);
		assertEquals(1, chain.length);
		assertArrayEquals(chain[0], genesis1.getHash(config.hashingForBlocks));
	}

	@Test
	@DisplayName("if a block with unknown previous is added, the head of the chain does not change")
	public void ifBlockWithUnknownPreviousIsAddedThenHeadIsNotChanged(@TempDir Path dir) throws NoSuchAlgorithmException, DatabaseException, URISyntaxException, InterruptedException, IOException, ClosedNodeException, VerificationException {
		var config = mkConfig(dir);
		var blockchain = mkTestBlockchain(config);
		var hashingForDeadlines = config.getHashingForDeadlines();
		var genesis = Blocks.genesis(LocalDateTime.now(ZoneId.of("UTC")), BigInteger.ONE);
		var deadline = Deadlines.of(new byte[] {80, 81, 83}, 13, new byte[] { 4, 5, 6 }, 11, new byte[] { 90, 91, 92 }, hashingForDeadlines);
		var unknownPrevious = new byte[] { 1, 2, 3, 4, 5, 6};
		var block = Blocks.of(13, BigInteger.TEN, 1234L, 1100L, BigInteger.valueOf(13011973), deadline, unknownPrevious);

		assertTrue(blockchain.add(genesis));
		assertFalse(blockchain.add(block));
		assertEquals(genesis, blockchain.getGenesis().get());
		assertEquals(genesis, blockchain.getHead().get());
		byte[][] chain = blockchain.getChain(0, 100).toArray(byte[][]::new);
		assertEquals(1, chain.length);
		assertArrayEquals(chain[0], genesis.getHash(config.hashingForBlocks));
	}

	@Test
	@DisplayName("if a block is added to the head of the chain, it becomes the head of the chain")
	public void ifBlockAddedToHeadOfChainThenItBecomesHead(@TempDir Path dir) throws NoSuchAlgorithmException, DatabaseException, URISyntaxException, InterruptedException, IOException, ClosedNodeException, VerificationException {
		var config = mkConfig(dir);
		var blockchain = mkTestBlockchain(config);
		var hashingForDeadlines = config.getHashingForDeadlines();
		var hashingForBlocks = config.getHashingForBlocks();
		var genesis = Blocks.genesis(LocalDateTime.now(ZoneId.of("UTC")), BigInteger.ONE);
		var deadline = Deadlines.of(new byte[] {80, 81, 83}, 13, new byte[] { 4, 5, 6 }, 11, new byte[] { 90, 91, 92 }, hashingForDeadlines);
		byte[] previous = genesis.getHash(hashingForBlocks);
		var block = Blocks.of(1, BigInteger.TEN, 1234L, 1100L, BigInteger.valueOf(13011973), deadline, previous);

		assertTrue(blockchain.add(genesis));
		assertTrue(blockchain.add(block));
		assertEquals(genesis, blockchain.getGenesis().get());
		assertEquals(block, blockchain.getHead().get());
		byte[][] chain = blockchain.getChain(0, 100).toArray(byte[][]::new);
		assertEquals(2, chain.length);
		assertArrayEquals(chain[0], genesis.getHash(config.hashingForBlocks));
		assertArrayEquals(chain[1], block.getHash(config.hashingForBlocks));
	}

	@Test
	@DisplayName("if a block is added to the chain but head has more power, the head of the chain is not changed")
	public void ifBlockAddedToChainButHeadBetterThenHeadIsNotChanged(@TempDir Path dir) throws NoSuchAlgorithmException, DatabaseException, URISyntaxException, InterruptedException, IOException, ClosedNodeException, VerificationException {
		var config = mkConfig(dir);
		var blockchain = mkTestBlockchain(config);
		var hashingForDeadlines = config.getHashingForDeadlines();
		var hashingForBlocks = config.getHashingForBlocks();
		var genesis = Blocks.genesis(LocalDateTime.now(ZoneId.of("UTC")), BigInteger.ONE);
		var deadline = Deadlines.of(new byte[] {80, 81, 83}, 13, new byte[] { 4, 5, 6 }, 11, new byte[] { 90, 91, 92 }, hashingForDeadlines);
		byte[] previous = genesis.getHash(hashingForBlocks);
		var block1 = Blocks.of(1, BigInteger.TEN, 1234L, 1100L, BigInteger.valueOf(13011973), deadline, previous);
		var added = Blocks.of(1, BigInteger.valueOf(15), 4321L, 1000L, BigInteger.valueOf(13011973), deadline, previous);
		previous = block1.getHash(hashingForBlocks);
		var block2 = Blocks.of(2, BigInteger.valueOf(20), 1234L, 1100L, BigInteger.valueOf(13011973), deadline, previous);
		previous = block2.getHash(hashingForBlocks);
		var block3 = Blocks.of(3, BigInteger.valueOf(30), 1234L, 1100L, BigInteger.valueOf(13011973), deadline, previous);

		assertTrue(blockchain.add(genesis));
		assertTrue(blockchain.add(block1));
		assertTrue(blockchain.add(block2));
		assertTrue(blockchain.add(block3));
		assertTrue(blockchain.add(added));
		assertEquals(genesis, blockchain.getGenesis().get());
		assertEquals(block3, blockchain.getHead().get());
		byte[][] chain = blockchain.getChain(0, 100).toArray(byte[][]::new);
		assertEquals(4, chain.length);
		assertArrayEquals(chain[0], genesis.getHash(config.hashingForBlocks));
		assertArrayEquals(chain[1], block1.getHash(config.hashingForBlocks));
		assertArrayEquals(chain[2], block2.getHash(config.hashingForBlocks));
		assertArrayEquals(chain[3], block3.getHash(config.hashingForBlocks));
	}

	@Test
	@DisplayName("if a chain with more power than the current chain is added, then it becomes the current chain")
	public void ifLongerChainIsAddedThenItBecomesTheCurrentChain(@TempDir Path dir) throws NoSuchAlgorithmException, DatabaseException, URISyntaxException, ClosedNodeException, InterruptedException, IOException, VerificationException {
		var config = mkConfig(dir);
		var blockchain = mkTestBlockchain(config);
		var hashingForDeadlines = config.getHashingForDeadlines();
		var hashingForBlocks = config.getHashingForBlocks();
		var genesis = Blocks.genesis(LocalDateTime.now(ZoneId.of("UTC")), BigInteger.ONE);
		var deadline = Deadlines.of(new byte[] {80, 81, 83}, 13, new byte[] { 4, 5, 6 }, 11, new byte[] { 90, 91, 92 }, hashingForDeadlines);
		byte[] previous = genesis.getHash(hashingForBlocks);
		var block1 = Blocks.of(1, BigInteger.valueOf(11), 1234L, 1100L, BigInteger.valueOf(13011973), deadline, previous);
		var block0 = Blocks.of(1, BigInteger.TEN, 4321L, 1000L, BigInteger.valueOf(13011973), deadline, previous);
		previous = block1.getHash(hashingForBlocks);
		var block2 = Blocks.of(2, BigInteger.valueOf(18), 1234L, 1100L, BigInteger.valueOf(13011973), deadline, previous);
		previous = block2.getHash(hashingForBlocks);
		var block3 = Blocks.of(3, BigInteger.valueOf(26), 1234L, 1100L, BigInteger.valueOf(13011973), deadline, previous);

		assertTrue(blockchain.add(genesis));
		assertTrue(blockchain.add(block0));

		// at this stage, block0 is the head of the current chain, of length 2
		assertEquals(genesis, blockchain.getGenesis().get());
		assertEquals(block0, blockchain.getHead().get());
		byte[][] chain = blockchain.getChain(0, 100).toArray(byte[][]::new);
		assertEquals(2, chain.length);
		assertArrayEquals(chain[0], genesis.getHash(config.hashingForBlocks));
		assertArrayEquals(chain[1], block0.getHash(config.hashingForBlocks));

		// we add an orphan (no previous in database)
		assertFalse(blockchain.add(block3));

		// nothing changes
		assertEquals(genesis, blockchain.getGenesis().get());
		assertEquals(block0, blockchain.getHead().get());
		chain = blockchain.getChain(0, 100).toArray(byte[][]::new);
		assertEquals(2, chain.length);
		assertArrayEquals(chain[0], genesis.getHash(config.hashingForBlocks));
		assertArrayEquals(chain[1], block0.getHash(config.hashingForBlocks));

		// we add an orphan (no previous in database)
		assertFalse(blockchain.add(block2));

		// nothing changes
		assertEquals(genesis, blockchain.getGenesis().get());
		assertEquals(block0, blockchain.getHead().get());
		chain = blockchain.getChain(0, 100).toArray(byte[][]::new);
		assertEquals(2, chain.length);
		assertArrayEquals(chain[0], genesis.getHash(config.hashingForBlocks));
		assertArrayEquals(chain[1], block0.getHash(config.hashingForBlocks));

		// we add a block after the genesis, that creates a better chain of length 4
		assertTrue(blockchain.add(block1));

		// the longer chain is the current chain now
		assertEquals(genesis, blockchain.getGenesis().get());
		assertEquals(block3, blockchain.getHead().get());
		chain = blockchain.getChain(0, 100).toArray(byte[][]::new);
		assertEquals(4, chain.length);
		assertArrayEquals(chain[0], genesis.getHash(config.hashingForBlocks));
		assertArrayEquals(chain[1], block1.getHash(config.hashingForBlocks));
		assertArrayEquals(chain[2], block2.getHash(config.hashingForBlocks));
		assertArrayEquals(chain[3], block3.getHash(config.hashingForBlocks));
	}

	@Test
	@DisplayName("if more children of the head are added, the one with higher power becomes head")
	public void ifMoreChildrenThanHigherPowerBecomesHead(@TempDir Path dir) throws NoSuchAlgorithmException, DatabaseException, URISyntaxException, ClosedNodeException, InterruptedException, IOException, VerificationException {
		var config = mkConfig(dir);
		var blockchain = mkTestBlockchain(config);
		var hashingForDeadlines = config.getHashingForDeadlines();
		var hashingForBlocks = config.getHashingForBlocks();
		var genesis = Blocks.genesis(LocalDateTime.now(ZoneId.of("UTC")), BigInteger.ONE);
		var deadline = Deadlines.of(new byte[] {80, 81, 83}, 13, new byte[] { 4, 5, 6 }, 11, new byte[] { 90, 91, 92 }, hashingForDeadlines);
		byte[] previous = genesis.getHash(hashingForBlocks);
		var block1 = Blocks.of(1, BigInteger.TEN, 4321L, 1100L, BigInteger.valueOf(13011973), deadline, previous);
		var block2 = Blocks.of(1, BigInteger.valueOf(11), 1234L, 1000L, BigInteger.valueOf(13011973), deadline, previous);
		var block3 = Blocks.of(1, BigInteger.valueOf(11), 2234L, 1000L, BigInteger.valueOf(13011973), deadline, previous);

		assertTrue(blockchain.add(genesis));
		assertTrue(blockchain.add(block1));

		// at this stage, block1 is the head of the current chain, of length 2
		assertEquals(genesis, blockchain.getGenesis().get());
		assertEquals(block1, blockchain.getHead().get());
		byte[][] chain = blockchain.getChain(0, 100).toArray(byte[][]::new);
		assertEquals(2, chain.length);
		assertArrayEquals(chain[0], genesis.getHash(config.hashingForBlocks));
		assertArrayEquals(chain[1], block1.getHash(config.hashingForBlocks));

		// we create a chain with more power as the current chain (11 vs 10),
		assertTrue(blockchain.add(block2));

		// block2 is the new head now
		assertEquals(genesis, blockchain.getGenesis().get());
		assertEquals(block2, blockchain.getHead().get());
		chain = blockchain.getChain(0, 100).toArray(byte[][]::new);
		assertEquals(2, chain.length);
		assertArrayEquals(chain[0], genesis.getHash(config.hashingForBlocks));
		assertArrayEquals(chain[1], block2.getHash(config.hashingForBlocks));

		// we create a chain with the same length as the current chain (2 blocks),
		// but same power as the current head (11 vs 11)
		assertTrue(blockchain.add(block3));

		// block2 is still the head
		assertEquals(genesis, blockchain.getGenesis().get());
		assertEquals(block2, blockchain.getHead().get());
		chain = blockchain.getChain(0, 100).toArray(byte[][]::new);
		assertEquals(2, chain.length);
		assertArrayEquals(chain[0], genesis.getHash(config.hashingForBlocks));
		assertArrayEquals(chain[1], block2.getHash(config.hashingForBlocks));
	}

	@Test
	@DisplayName("if the more powerful chain is added with genesis at the root, then it becomes the current chain")
	public void ifMorePowerfulChainAddedWithGenesisAtTheRootThenItBecomesCurrentChain(@TempDir Path dir) throws NoSuchAlgorithmException, DatabaseException, URISyntaxException, ClosedNodeException, InterruptedException, IOException, VerificationException {
		var config = mkConfig(dir);
		var blockchain = mkTestBlockchain(config);
		var hashingForDeadlines = config.getHashingForDeadlines();
		var hashingForBlocks = config.getHashingForBlocks();
		var genesis = Blocks.genesis(LocalDateTime.now(ZoneId.of("UTC")), BigInteger.ONE);
		var deadline = Deadlines.of(new byte[] {80, 81, 83}, 13, new byte[] { 4, 5, 6 }, 11, new byte[] { 90, 91, 92 }, hashingForDeadlines);
		byte[] previous = genesis.getHash(hashingForBlocks);
		var block1 = Blocks.of(1, BigInteger.TEN, 1234L, 1100L, BigInteger.valueOf(13011973), deadline, previous);
		previous = block1.getHash(hashingForBlocks);
		var block2 = Blocks.of(2, BigInteger.valueOf(20), 1234L, 1100L, BigInteger.valueOf(13011973), deadline, previous);
		previous = block2.getHash(hashingForBlocks);
		var block3 = Blocks.of(3, BigInteger.valueOf(30), 1234L, 1100L, BigInteger.valueOf(13011973), deadline, previous);

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
		assertArrayEquals(chain[0], genesis.getHash(config.hashingForBlocks));
		assertArrayEquals(chain[1], block1.getHash(config.hashingForBlocks));
		assertArrayEquals(chain[2], block2.getHash(config.hashingForBlocks));
		assertArrayEquals(chain[3], block3.getHash(config.hashingForBlocks));
	}

	static {
		String current = System.getProperty("java.util.logging.config.file");
		if (current == null) {
			// if the property is not set, we provide a default (if it exists)
			URL resource = BlockAdditionTests.class.getClassLoader().getResource("logging.properties");
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