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
import static org.mockito.Mockito.mock;

import java.io.IOException;
import java.math.BigInteger;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Path;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.function.Function;
import java.util.logging.LogManager;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import io.hotmoka.crypto.HashingAlgorithms;
import io.mokamint.application.api.Application;
import io.mokamint.node.Blocks;
import io.mokamint.node.api.ChainInfo;
import io.mokamint.node.api.ClosedNodeException;
import io.mokamint.node.api.DatabaseException;
import io.mokamint.node.local.Config;
import io.mokamint.node.local.internal.LocalNodeImpl;
import io.mokamint.nonce.Deadlines;

public class BlockAdditionTests {

	private static Config mkConfig(Path dir) throws NoSuchAlgorithmException {
		return Config.Builder.defaults()
			.setDir(dir)
			.build();
	}

	private static class MyLocalNode extends LocalNodeImpl {

		public MyLocalNode(Config config) throws NoSuchAlgorithmException, DatabaseException, IOException {
			super(config, mock(Application.class));
		}

		@Override
		protected boolean isDisabled(Event event) {
			// we disable the events responsible for mining, so that they do not interfere with the tests
			// that add blocks to the chain
			return event instanceof BlockDiscoveryEvent || super.isDisabled(event);
		}
	}

	@Test
	@DisplayName("if a block with unknown previous is added, the head of the chain does not change")
	public void ifBlockWithUnknownPreviousIsAddedThenHeadIsNotChanged(@TempDir Path dir) throws NoSuchAlgorithmException, DatabaseException, URISyntaxException, InterruptedException, IOException, ClosedNodeException {
		var genesis = Blocks.genesis(LocalDateTime.now(ZoneId.of("UTC")));
		var hashing = HashingAlgorithms.shabal256(Function.identity());
		var deadline = Deadlines.of(new byte[] {80, 81, 83}, 13, new byte[] { 4, 5, 6 }, 11, new byte[] { 90, 91, 92 }, hashing);
		byte[] unknownPrevious = new byte[] { 1, 2, 3, 4, 5, 6};
		var block = Blocks.of(13, 1234L, 1100L, BigInteger.valueOf(13011973), deadline, unknownPrevious);
		var config = mkConfig(dir);

		try (var node = new MyLocalNode(config)) {
			assertTrue(node.add(genesis));
			assertFalse(node.add(block));
			ChainInfo info = node.getChainInfo();
			assertEquals(genesis, node.getBlock(info.getGenesisHash().get()).get());
			assertEquals(genesis, node.getBlock(info.getHeadHash().get()).get());
		}
	}

	@Test
	@DisplayName("if a block added to the head of the chain, it becomes the head of the chain")
	public void ifBlockAddedToHeadOfChainThenItBecomesHead(@TempDir Path dir) throws NoSuchAlgorithmException, DatabaseException, URISyntaxException, InterruptedException, IOException, ClosedNodeException {
		var genesis = Blocks.genesis(LocalDateTime.now(ZoneId.of("UTC")));
		var hashing = HashingAlgorithms.shabal256(Function.identity());
		var deadline = Deadlines.of(new byte[] {80, 81, 83}, 13, new byte[] { 4, 5, 6 }, 11, new byte[] { 90, 91, 92 }, hashing);
		var config = mkConfig(dir);
		byte[] previous = genesis.getHash(config.getHashingForBlocks());
		var block = Blocks.of(1, 1234L, 1100L, BigInteger.valueOf(13011973), deadline, previous);

		try (var node = new MyLocalNode(config)) {
			assertTrue(node.add(genesis));
			assertTrue(node.add(block));
			ChainInfo info = node.getChainInfo();
			assertEquals(genesis, node.getBlock(info.getGenesisHash().get()).get());
			assertEquals(block, node.getBlock(info.getHeadHash().get()).get());
		}
	}

	@Test
	@DisplayName("if a block added to the chain but head is higher, the head of the chain is not changed")
	public void ifBlockAddedToChainButHeadHigherThenHeadIsNotChanged(@TempDir Path dir) throws NoSuchAlgorithmException, DatabaseException, URISyntaxException, InterruptedException, IOException, ClosedNodeException {
		var genesis = Blocks.genesis(LocalDateTime.now(ZoneId.of("UTC")));
		var hashing = HashingAlgorithms.shabal256(Function.identity());
		var deadline = Deadlines.of(new byte[] {80, 81, 83}, 13, new byte[] { 4, 5, 6 }, 11, new byte[] { 90, 91, 92 }, hashing);
		var config = mkConfig(dir);
		byte[] previous = genesis.getHash(config.getHashingForBlocks());
		var block1 = Blocks.of(1, 1234L, 1100L, BigInteger.valueOf(13011973), deadline, previous);
		var added = Blocks.of(1, 4321L, 1000L, BigInteger.valueOf(13011973), deadline, previous);
		previous = block1.getHash(config.getHashingForBlocks());
		var block2 = Blocks.of(2, 1234L, 1100L, BigInteger.valueOf(13011973), deadline, previous);
		previous = block2.getHash(config.getHashingForBlocks());
		var block3 = Blocks.of(3, 1234L, 1100L, BigInteger.valueOf(13011973), deadline, previous);

		try (var node = new MyLocalNode(config)) {
			assertTrue(node.add(genesis));
			assertTrue(node.add(block1));
			assertTrue(node.add(block2));
			assertTrue(node.add(block3));
			assertTrue(node.add(added));
			ChainInfo info = node.getChainInfo();
			assertEquals(genesis, node.getBlock(info.getGenesisHash().get()).get());
			assertEquals(block3, node.getBlock(info.getHeadHash().get()).get());
		}
	}

	@Test
	@DisplayName("if a chain longer than the current chain is added, then it becomes the current chain")
	public void ifLongerChainIsAddedThenItBecomesTheCurrentChain(@TempDir Path dir) throws NoSuchAlgorithmException, DatabaseException, URISyntaxException, ClosedNodeException, InterruptedException, IOException {
		var genesis = Blocks.genesis(LocalDateTime.now(ZoneId.of("UTC")));
		var hashing = HashingAlgorithms.shabal256(Function.identity());
		var deadline = Deadlines.of(new byte[] {80, 81, 83}, 13, new byte[] { 4, 5, 6 }, 11, new byte[] { 90, 91, 92 }, hashing);
		var config = mkConfig(dir);
		byte[] previous = genesis.getHash(config.getHashingForBlocks());
		var block1 = Blocks.of(1, 1234L, 1100L, BigInteger.valueOf(13011973), deadline, previous);
		var block0 = Blocks.of(1, 4321L, 1000L, BigInteger.valueOf(13011973), deadline, previous);
		previous = block1.getHash(config.getHashingForBlocks());
		var block2 = Blocks.of(2, 1234L, 1100L, BigInteger.valueOf(13011973), deadline, previous);
		previous = block2.getHash(config.getHashingForBlocks());
		var block3 = Blocks.of(3, 1234L, 1100L, BigInteger.valueOf(13011973), deadline, previous);

		try (var node = new MyLocalNode(config)) {
			assertTrue(node.add(genesis));
			assertTrue(node.add(block0));
			
			// at this stage, block0 is the head of the current chain, of length 2
			ChainInfo info = node.getChainInfo();
			assertEquals(genesis, node.getBlock(info.getGenesisHash().get()).get());
			assertEquals(block0, node.getBlock(info.getHeadHash().get()).get());

			// we add an orphan (no previous in database)
			assertFalse(node.add(block3));

			// nothing changes
			info = node.getChainInfo();
			assertEquals(genesis, node.getBlock(info.getGenesisHash().get()).get());
			assertEquals(block0, node.getBlock(info.getHeadHash().get()).get());

			// we add an orphan (no previous in database)
			assertFalse(node.add(block2));

			// nothing changes
			info = node.getChainInfo();
			assertEquals(genesis, node.getBlock(info.getGenesisHash().get()).get());
			assertEquals(block0, node.getBlock(info.getHeadHash().get()).get());

			// we add a block after the genesis, that creates a chain of length 4
			assertTrue(node.add(block1));

			// the longer chain is the current chain now
			info = node.getChainInfo();
			assertEquals(genesis, node.getBlock(info.getGenesisHash().get()).get());
			assertEquals(block3, node.getBlock(info.getHeadHash().get()).get());
		}
	}

	@Test
	@DisplayName("if a chain of the same length as the current chain is added, the chain with smaller total time becomes the current chain")
	public void ifSameLengthChainIsAddedThenTotalTimeMatters(@TempDir Path dir) throws NoSuchAlgorithmException, DatabaseException, URISyntaxException, ClosedNodeException, InterruptedException, IOException {
		var genesis = Blocks.genesis(LocalDateTime.now(ZoneId.of("UTC")));
		var hashing = HashingAlgorithms.shabal256(Function.identity());
		var deadline = Deadlines.of(new byte[] {80, 81, 83}, 13, new byte[] { 4, 5, 6 }, 11, new byte[] { 90, 91, 92 }, hashing);
		var config = mkConfig(dir);
		byte[] previous = genesis.getHash(config.getHashingForBlocks());
		var block1 = Blocks.of(1, 4321L, 1100L, BigInteger.valueOf(13011973), deadline, previous);
		var block2 = Blocks.of(1, 1234L, 1000L, BigInteger.valueOf(13011973), deadline, previous);
		var block3 = Blocks.of(1, 2234L, 1000L, BigInteger.valueOf(13011973), deadline, previous);

		try (var node = new MyLocalNode(config)) {
			assertTrue(node.add(genesis));
			assertTrue(node.add(block1));
			
			// at this stage, block1 is the head of the current chain, of length 2
			ChainInfo info = node.getChainInfo();
			assertEquals(genesis, node.getBlock(info.getGenesisHash().get()).get());
			assertEquals(block1, node.getBlock(info.getHeadHash().get()).get());

			// we create a chain with the same length as the current chain (2 blocks),
			// but smaller waiting time (1234 instead of 4321)
			assertTrue(node.add(block2));

			// block2 is the new head
			info = node.getChainInfo();
			assertEquals(genesis, node.getBlock(info.getGenesisHash().get()).get());
			assertEquals(block2, node.getBlock(info.getHeadHash().get()).get());

			// we create a chain with the same length as the current chain (2 blocks),
			// but larger waiting time (2234 instead of 1234)
			assertTrue(node.add(block3));

			// block2 is still the head
			info = node.getChainInfo();
			assertEquals(genesis, node.getBlock(info.getGenesisHash().get()).get());
			assertEquals(block2, node.getBlock(info.getHeadHash().get()).get());
		}
	}

	@Test
	@DisplayName("if the longest chain is added with genesis at the root, then it becomes the current chain")
	public void ifLongestChainAddedWithGenesisAtTheRootThenItBecomesCurrentChain(@TempDir Path dir) throws NoSuchAlgorithmException, DatabaseException, URISyntaxException, ClosedNodeException, InterruptedException, IOException {
		var genesis = Blocks.genesis(LocalDateTime.now(ZoneId.of("UTC")));
		var hashing = HashingAlgorithms.shabal256(Function.identity());
		var deadline = Deadlines.of(new byte[] {80, 81, 83}, 13, new byte[] { 4, 5, 6 }, 11, new byte[] { 90, 91, 92 }, hashing);
		var config = mkConfig(dir);
		byte[] previous = genesis.getHash(config.getHashingForBlocks());
		var block1 = Blocks.of(1, 1234L, 1100L, BigInteger.valueOf(13011973), deadline, previous);
		previous = block1.getHash(config.getHashingForBlocks());
		var block2 = Blocks.of(2, 1234L, 1100L, BigInteger.valueOf(13011973), deadline, previous);
		previous = block2.getHash(config.getHashingForBlocks());
		var block3 = Blocks.of(3, 1234L, 1100L, BigInteger.valueOf(13011973), deadline, previous);

		try (var node = new MyLocalNode(config)) {
			assertFalse(node.add(block3));

			// no genesis and no head are set up to now
			ChainInfo info = node.getChainInfo();
			assertTrue(info.getGenesisHash().isEmpty());
			assertTrue(info.getHeadHash().isEmpty());

			assertFalse(node.add(block2));

			// no genesis and no head are set up to now
			info = node.getChainInfo();
			assertTrue(info.getGenesisHash().isEmpty());
			assertTrue(info.getHeadHash().isEmpty());

			assertFalse(node.add(block1));

			// no genesis and no head are set up to now
			info = node.getChainInfo();
			assertTrue(info.getGenesisHash().isEmpty());
			assertTrue(info.getHeadHash().isEmpty());

			assertTrue(node.add(genesis));

			// genesis and head are set now
			info = node.getChainInfo();
			assertEquals(genesis, node.getBlock(info.getGenesisHash().get()).get());
			assertEquals(block3, node.getBlock(info.getHeadHash().get()).get());
		}
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