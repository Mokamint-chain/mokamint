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
import java.util.logging.LogManager;
import java.util.stream.Stream;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

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

public class VerificationTests {

	private static Config mkConfig(Path dir) throws NoSuchAlgorithmException {
		return Config.Builder.defaults()
			.setDir(dir)
			.setBlockMaxTimeInTheFuture(1000L)
			.build();
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

	@Test
	@DisplayName("if an added block is too much in the future, verification rejects it")
	public void blockTooMuchInTheFutureGetsRejected(@TempDir Path dir) throws NoSuchAlgorithmException, DatabaseException, InterruptedException, VerificationException, ClosedDatabaseException {
		var config = mkConfig(dir);
		var blockchain = mkTestBlockchain(config);
		var hashingForDeadlines = config.getHashingForDeadlines();
		var hashingForBlocks = config.getHashingForBlocks();
		var genesis = Blocks.genesis(LocalDateTime.now(ZoneId.of("UTC")), BigInteger.ONE);
		var deadline = Deadlines.of(new byte[] {80, 81, 83}, 13, new byte[] { 4, 5, 6 }, 11, new byte[] { 90, 91, 92 }, hashingForDeadlines);
		byte[] previous = genesis.getHash(hashingForBlocks);
		var block = Blocks.of(1, BigInteger.TEN, 12345678L, 1100L, BigInteger.valueOf(13011973), deadline, previous);

		assertTrue(blockchain.add(genesis));
		VerificationException e = assertThrows(VerificationException.class, () -> blockchain.add(block));
		assertTrue(e.getMessage().contains("Too much in the future"));
		assertEquals(genesis, blockchain.getGenesis().get());
		assertEquals(genesis, blockchain.getHead().get());
		byte[][] chain = blockchain.getChain(0, 100).toArray(byte[][]::new);
		assertEquals(1, chain.length);
		assertArrayEquals(chain[0], genesis.getHash(config.hashingForBlocks));
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