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
import static org.mockito.AdditionalAnswers.returnsFirstArg;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.math.BigInteger;
import java.net.URL;
import java.nio.file.Path;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.function.Function;
import java.util.logging.LogManager;
import java.util.stream.Stream;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import io.hotmoka.crypto.HashingAlgorithms;
import io.mokamint.application.api.Application;
import io.mokamint.node.Blocks;
import io.mokamint.node.api.DatabaseException;
import io.mokamint.node.local.Config;
import io.mokamint.node.local.internal.ClosedDatabaseException;
import io.mokamint.node.local.internal.LocalNodeImpl;
import io.mokamint.node.local.internal.NodeMiners;
import io.mokamint.node.local.internal.NodePeers;
import io.mokamint.node.local.internal.blockchain.Blockchain;
import io.mokamint.node.local.internal.blockchain.VerificationException;
import io.mokamint.nonce.Deadlines;
import io.mokamint.plotter.Plots;
import io.mokamint.plotter.api.Plot;

public class VerificationTests {

	/**
	 * The plot used for creating the deadlines.
	 */
	private static Plot plot;

	@BeforeAll
	public static void beforeAll(@TempDir Path plotDir) throws IOException {
		var prolog = new byte[] { 11, 13, 24, 88 };
		long start = 65536L;
		long length = 50L;
		var hashing = HashingAlgorithms.shabal256(Function.identity());

		plot = Plots.create(plotDir.resolve("plot.plot"), prolog, start, length, hashing, __ -> {});
	}

	@AfterAll
	public static void afterAll() throws IOException {
		plot.close();
	}

	@Test
	@DisplayName("if an added non-genesis block is too much in the future, verification rejects it")
	public void blockTooMuchInTheFutureGetsRejected(@TempDir Path dir) throws NoSuchAlgorithmException, DatabaseException, InterruptedException, VerificationException, ClosedDatabaseException {
		var config = Config.Builder.defaults()
				.setDir(dir)
				.setBlockMaxTimeInTheFuture(1000)
				.build();
		var blockchain = mkTestBlockchain(config);
		var hashingForDeadlines = config.getHashingForDeadlines();
		var hashingForBlocks = config.getHashingForBlocks();
		var genesis = Blocks.genesis(LocalDateTime.now(ZoneId.of("UTC")), BigInteger.ONE);
		var deadline = Deadlines.of(new byte[] {80, 81, 83}, 13, new byte[] { 4, 5, 6 }, 11, new byte[] { 90, 91, 92 }, hashingForDeadlines);
		byte[] previous = genesis.getHash(hashingForBlocks);
		var block = Blocks.of(1, BigInteger.TEN, config.blockMaxTimeInTheFuture + 1000, 1100L, BigInteger.valueOf(13011973), deadline, previous);

		assertTrue(blockchain.add(genesis));
		VerificationException e = assertThrows(VerificationException.class, () -> blockchain.add(block));
		assertTrue(e.getMessage().contains("Too much in the future"));
		assertEquals(genesis, blockchain.getGenesis().get());
		assertEquals(genesis, blockchain.getHead().get());
		byte[][] chain = blockchain.getChain(0, 100).toArray(byte[][]::new);
		assertEquals(1, chain.length);
		assertArrayEquals(chain[0], genesis.getHash(config.hashingForBlocks));
	}

	@Test
	@DisplayName("if an added genesis block is too much in the future, verification rejects it")
	public void genesisTooMuchInTheFutureGetsRejected(@TempDir Path dir) throws NoSuchAlgorithmException, DatabaseException, InterruptedException, VerificationException, ClosedDatabaseException {
		var config = Config.Builder.defaults()
			.setDir(dir)
			.setBlockMaxTimeInTheFuture(1000)
			.build();
		var blockchain = mkTestBlockchain(config);
		var genesis1 = Blocks.genesis(LocalDateTime.now(ZoneId.of("UTC")), BigInteger.ONE);
		var genesis2 = Blocks.genesis(genesis1.getStartDateTimeUTC().plus(config.blockMaxTimeInTheFuture + 1000, ChronoUnit.MINUTES), BigInteger.ONE);

		assertTrue(blockchain.add(genesis1));
		VerificationException e = assertThrows(VerificationException.class, () -> blockchain.add(genesis2));
		assertTrue(e.getMessage().contains("Too much in the future"));
		assertEquals(genesis1, blockchain.getGenesis().get());
		assertEquals(genesis1, blockchain.getHead().get());
		byte[][] chain = blockchain.getChain(0, 100).toArray(byte[][]::new);
		assertEquals(1, chain.length);
		assertArrayEquals(chain[0], genesis1.getHash(config.hashingForBlocks));
	}

	@Test
	@DisplayName("if an added non-genesis block has inconsistent height, verification rejects it")
	public void blockHeightMismatchGetsRejected(@TempDir Path dir) throws NoSuchAlgorithmException, DatabaseException, InterruptedException, VerificationException, ClosedDatabaseException, IOException {
		var config = Config.Builder.defaults()
				.setDir(dir)
				// we effectively disable the time check
				.setBlockMaxTimeInTheFuture(Long.MAX_VALUE)
				.build();

		var blockchain = mkTestBlockchain(config);
		var hashingForDeadlines = config.getHashingForDeadlines();
		var hashingForBlocks = config.getHashingForBlocks();
		var hashingForGenerations = config.getHashingForGenerations();
		var genesis = Blocks.genesis(LocalDateTime.now(ZoneId.of("UTC")), BigInteger.ONE);
		var nextDeadlineDescription = genesis.getNextDeadlineDescription(hashingForGenerations, hashingForDeadlines);
		var deadline = plot.getSmallestDeadline(nextDeadlineDescription);
		var expected = genesis.getNextBlockDescription(deadline, config.targetBlockCreationTime, hashingForBlocks, hashingForDeadlines);

		// we replace the expected block hash
		var block = Blocks.of(expected.getHeight() + 1, expected.getPower(), expected.getTotalWaitingTime(), expected.getWeightedWaitingTime(), expected.getAcceleration(),
				expected.getDeadline(), expected.getHashOfPreviousBlock());

		assertTrue(blockchain.add(genesis));
		VerificationException e = assertThrows(VerificationException.class, () -> blockchain.add(block));
		assertTrue(e.getMessage().contains("Height mismatch"));
		assertEquals(genesis, blockchain.getGenesis().get());
		assertEquals(genesis, blockchain.getHead().get());
		byte[][] chain = blockchain.getChain(0, 100).toArray(byte[][]::new);
		assertEquals(1, chain.length);
		assertArrayEquals(chain[0], genesis.getHash(config.hashingForBlocks));
	}

	private static Blockchain mkTestBlockchain(Config config) throws DatabaseException {
		var peers = mock(NodePeers.class);
		doAnswer(returnsFirstArg())
			.when(peers)
			.asNetworkDateTime(any());
	
		var node = mock(LocalNodeImpl.class);
		when(node.getConfig()).thenReturn(config);
		when(node.getApplication()).thenReturn(mock(Application.class));
		when(node.getPeers()).thenReturn(peers);
		var miners = new NodeMiners(node, Stream.empty());
		when(node.getMiners()).thenReturn(miners);
		var blockchain = new Blockchain(node);
		when(node.getBlockchain()).thenReturn(blockchain);
	
		return blockchain;
	}

	static {
		String current = System.getProperty("java.util.logging.config.file");
		if (current == null) {
			// if the property is not set, we provide a default (if it exists)
			URL resource = VerificationTests.class.getClassLoader().getResource("logging.properties");
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