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
import java.nio.file.Path;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
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
import io.mokamint.node.api.DatabaseException;
import io.mokamint.node.api.GenesisBlock;
import io.mokamint.node.local.Config;
import io.mokamint.node.local.internal.ClosedDatabaseException;
import io.mokamint.node.local.internal.LocalNodeImpl;
import io.mokamint.node.local.internal.NodeMiners;
import io.mokamint.node.local.internal.NodePeers;
import io.mokamint.node.local.internal.blockchain.Blockchain;
import io.mokamint.node.local.internal.blockchain.VerificationException;
import io.mokamint.nonce.Deadlines;
import io.mokamint.nonce.Prologs;
import io.mokamint.nonce.api.Deadline;
import io.mokamint.nonce.api.Prolog;
import io.mokamint.plotter.Plots;
import io.mokamint.plotter.api.Plot;

public class VerificationTests extends AbstractLoggedTests {

	/**
	 * The plot used for creating the deadlines.
	 */
	private static Plot plot;

	/**
	 * The prolog of the deadlines.
	 */
	private static Prolog prolog;

	@BeforeAll
	public static void beforeAll(@TempDir Path plotDir) throws IOException, NoSuchAlgorithmException, InvalidKeyException {
		var ed25519 = SignatureAlgorithms.ed25519(Function.identity());
		prolog = Prologs.of("octopus", ed25519.getKeyPair().getPublic(), ed25519.getKeyPair().getPublic(), new byte[0]);
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
	public void blockTooMuchInTheFutureGetsRejected(@TempDir Path dir) throws NoSuchAlgorithmException, DatabaseException, VerificationException, ClosedDatabaseException, InvalidKeyException {
		var config = Config.Builder.defaults()
				.setDir(dir)
				.setBlockMaxTimeInTheFuture(1000)
				.setChainId("octopus")
				.build();
		var blockchain = mkTestBlockchain(config);
		var hashingForDeadlines = config.getHashingForDeadlines();
		var hashingForBlocks = config.getHashingForBlocks();
		var genesis = Blocks.genesis(LocalDateTime.now(ZoneId.of("UTC")), BigInteger.ONE);
		var value = new byte[hashingForDeadlines.length()];
		for (int pos = 0; pos < value.length; pos++)
			value[pos] = (byte) pos;
		var deadline = Deadlines.of(prolog, 13, value, 11, new byte[] { 90, 91, 92 }, hashingForDeadlines);
		byte[] previous = genesis.getHash(hashingForBlocks);
		var block = Blocks.of(1, BigInteger.TEN, config.blockMaxTimeInTheFuture + 1000, 1100L, BigInteger.valueOf(13011973), deadline, previous);

		assertTrue(blockchain.add(genesis));
		VerificationException e = assertThrows(VerificationException.class, () -> blockchain.add(block));
		assertTrue(e.getMessage().startsWith("Too much in the future"));
		assertBlockchainIsJustGenesis(blockchain, genesis, config);
	}

	@Test
	@DisplayName("if an added genesis block is too much in the future, verification rejects it")
	public void genesisTooMuchInTheFutureGetsRejected(@TempDir Path dir) throws NoSuchAlgorithmException, DatabaseException, VerificationException, ClosedDatabaseException {
		var config = Config.Builder.defaults()
			.setDir(dir)
			.setChainId("octopus")
			.setBlockMaxTimeInTheFuture(1000)
			.build();
		var blockchain = mkTestBlockchain(config);
		var genesis1 = Blocks.genesis(LocalDateTime.now(ZoneId.of("UTC")), BigInteger.ONE);
		var genesis2 = Blocks.genesis(genesis1.getStartDateTimeUTC().plus(config.blockMaxTimeInTheFuture + 1000, ChronoUnit.MINUTES), BigInteger.ONE);

		assertTrue(blockchain.add(genesis1));
		VerificationException e = assertThrows(VerificationException.class, () -> blockchain.add(genesis2));
		assertTrue(e.getMessage().startsWith("Too much in the future"));
		assertBlockchainIsJustGenesis(blockchain, genesis1, config);
	}

	@Test
	@DisplayName("if an added non-genesis block has inconsistent height, verification rejects it")
	public void blockHeightMismatchGetsRejected(@TempDir Path dir) throws NoSuchAlgorithmException, DatabaseException, VerificationException, ClosedDatabaseException, IOException {
		var config = mkConfig(dir);
		var blockchain = mkTestBlockchain(config);
		var hashingForDeadlines = config.getHashingForDeadlines();
		var genesis = Blocks.genesis(LocalDateTime.now(ZoneId.of("UTC")), BigInteger.ONE);
		var deadline = plot.getSmallestDeadline(genesis.getNextDeadlineDescription(config.getHashingForGenerations(), hashingForDeadlines));
		var expected = genesis.getNextBlockDescription(deadline, config.targetBlockCreationTime, config.getHashingForBlocks(), hashingForDeadlines);

		// we replace the expected block hash
		var block = Blocks.of(expected.getHeight() + 1, expected.getPower(), expected.getTotalWaitingTime(), expected.getWeightedWaitingTime(), expected.getAcceleration(),
				expected.getDeadline(), expected.getHashOfPreviousBlock());

		assertTrue(blockchain.add(genesis));
		VerificationException e = assertThrows(VerificationException.class, () -> blockchain.add(block));
		assertTrue(e.getMessage().startsWith("Height mismatch"));
		assertBlockchainIsJustGenesis(blockchain, genesis, config);
	}

	@Test
	@DisplayName("if an added non-genesis block has inconsistent acceleration, verification rejects it")
	public void accelerationMismatchGetsRejected(@TempDir Path dir) throws NoSuchAlgorithmException, DatabaseException, VerificationException, ClosedDatabaseException, IOException {
		var config = mkConfig(dir);
		var blockchain = mkTestBlockchain(config);
		var hashingForDeadlines = config.getHashingForDeadlines();
		var genesis = Blocks.genesis(LocalDateTime.now(ZoneId.of("UTC")), BigInteger.ONE);
		var deadline = plot.getSmallestDeadline(genesis.getNextDeadlineDescription(config.getHashingForGenerations(), hashingForDeadlines));
		var expected = genesis.getNextBlockDescription(deadline, config.targetBlockCreationTime, config.getHashingForBlocks(), hashingForDeadlines);

		// we replace the expected acceleration
		var block = Blocks.of(expected.getHeight(), expected.getPower(), expected.getTotalWaitingTime(), expected.getWeightedWaitingTime(), expected.getAcceleration().add(BigInteger.ONE),
				expected.getDeadline(), expected.getHashOfPreviousBlock());

		assertTrue(blockchain.add(genesis));
		VerificationException e = assertThrows(VerificationException.class, () -> blockchain.add(block));
		assertTrue(e.getMessage().startsWith("Acceleration mismatch"));
		assertBlockchainIsJustGenesis(blockchain, genesis, config);
	}

	@Test
	@DisplayName("if an added non-genesis block has inconsistent power, verification rejects it")
	public void powerMismatchGetsRejected(@TempDir Path dir) throws NoSuchAlgorithmException, DatabaseException, VerificationException, ClosedDatabaseException, IOException {
		var config = mkConfig(dir);
		var blockchain = mkTestBlockchain(config);
		var hashingForDeadlines = config.getHashingForDeadlines();
		var genesis = Blocks.genesis(LocalDateTime.now(ZoneId.of("UTC")), BigInteger.ONE);
		var deadline = plot.getSmallestDeadline(genesis.getNextDeadlineDescription(config.getHashingForGenerations(), hashingForDeadlines));
		var expected = genesis.getNextBlockDescription(deadline, config.targetBlockCreationTime, config.getHashingForBlocks(), hashingForDeadlines);

		// we replace the expected power
		var block = Blocks.of(expected.getHeight(), expected.getPower().add(BigInteger.ONE), expected.getTotalWaitingTime(), expected.getWeightedWaitingTime(), expected.getAcceleration(),
				expected.getDeadline(), expected.getHashOfPreviousBlock());

		assertTrue(blockchain.add(genesis));
		VerificationException e = assertThrows(VerificationException.class, () -> blockchain.add(block));
		assertTrue(e.getMessage().startsWith("Power mismatch"));
		assertBlockchainIsJustGenesis(blockchain, genesis, config);
	}

	@Test
	@DisplayName("if an added non-genesis block has inconsistent total waiting time, verification rejects it")
	public void totalWaitingTimeMismatchGetsRejected(@TempDir Path dir) throws NoSuchAlgorithmException, DatabaseException, VerificationException, ClosedDatabaseException, IOException {
		var config = mkConfig(dir);
		var blockchain = mkTestBlockchain(config);
		var hashingForDeadlines = config.getHashingForDeadlines();
		var genesis = Blocks.genesis(LocalDateTime.now(ZoneId.of("UTC")), BigInteger.ONE);
		var deadline = plot.getSmallestDeadline(genesis.getNextDeadlineDescription(config.getHashingForGenerations(), hashingForDeadlines));
		var expected = genesis.getNextBlockDescription(deadline, config.targetBlockCreationTime, config.getHashingForBlocks(), hashingForDeadlines);

		// we replace the expected total waiting time
		var block = Blocks.of(expected.getHeight(), expected.getPower(), expected.getTotalWaitingTime() + 1, expected.getWeightedWaitingTime(), expected.getAcceleration(),
				expected.getDeadline(), expected.getHashOfPreviousBlock());

		assertTrue(blockchain.add(genesis));
		VerificationException e = assertThrows(VerificationException.class, () -> blockchain.add(block));
		assertTrue(e.getMessage().startsWith("Total waiting time mismatch"));
		assertBlockchainIsJustGenesis(blockchain, genesis, config);
	}

	@Test
	@DisplayName("if an added non-genesis block has inconsistent deadline's scoop number, verification rejects it")
	public void deadlineScoopNumberMismatchGetsRejected(@TempDir Path dir) throws NoSuchAlgorithmException, DatabaseException, VerificationException, ClosedDatabaseException, IOException {
		var config = mkConfig(dir);
		var blockchain = mkTestBlockchain(config);
		var hashingForDeadlines = config.getHashingForDeadlines();
		var genesis = Blocks.genesis(LocalDateTime.now(ZoneId.of("UTC")), BigInteger.ONE);
		var deadline = plot.getSmallestDeadline(genesis.getNextDeadlineDescription(config.getHashingForGenerations(), hashingForDeadlines));
		var expected = genesis.getNextBlockDescription(deadline, config.targetBlockCreationTime, config.getHashingForBlocks(), hashingForDeadlines);

		// we replace the expected deadline
		var modifiedDeadline = Deadlines.of(deadline.getProlog(), deadline.getProgressive(), deadline.getValue(),
				(deadline.getScoopNumber() + 1) % Deadline.MAX_SCOOP_NUMBER, deadline.getData(), deadline.getHashing());
		var block = Blocks.of(expected.getHeight(), expected.getPower(), expected.getTotalWaitingTime(), expected.getWeightedWaitingTime(), expected.getAcceleration(),
				modifiedDeadline, expected.getHashOfPreviousBlock());

		assertTrue(blockchain.add(genesis));
		VerificationException e = assertThrows(VerificationException.class, () -> blockchain.add(block));
		assertTrue(e.getMessage().startsWith("Deadline mismatch"));
		assertBlockchainIsJustGenesis(blockchain, genesis, config);
	}

	@Test
	@DisplayName("if an added non-genesis block has inconsistent deadline's data, verification rejects it")
	public void deadlineDataMismatchGetsRejected(@TempDir Path dir) throws NoSuchAlgorithmException, DatabaseException, VerificationException, ClosedDatabaseException, IOException {
		var config = mkConfig(dir);
		var blockchain = mkTestBlockchain(config);
		var hashingForDeadlines = config.getHashingForDeadlines();
		var genesis = Blocks.genesis(LocalDateTime.now(ZoneId.of("UTC")), BigInteger.ONE);
		var deadline = plot.getSmallestDeadline(genesis.getNextDeadlineDescription(config.getHashingForGenerations(), hashingForDeadlines));
		var expected = genesis.getNextBlockDescription(deadline, config.targetBlockCreationTime, config.getHashingForBlocks(), hashingForDeadlines);

		// we replace the expected deadline
		var modifiedData = deadline.getData();
		// blocks' deadlines have a non-empty data array
		modifiedData[0]++;
		var modifiedDeadline = Deadlines.of(deadline.getProlog(), deadline.getProgressive(), deadline.getValue(),
				deadline.getScoopNumber(), modifiedData, deadline.getHashing());
		var block = Blocks.of(expected.getHeight(), expected.getPower(), expected.getTotalWaitingTime(), expected.getWeightedWaitingTime(), expected.getAcceleration(),
				modifiedDeadline, expected.getHashOfPreviousBlock());

		assertTrue(blockchain.add(genesis));
		VerificationException e = assertThrows(VerificationException.class, () -> blockchain.add(block));
		assertTrue(e.getMessage().startsWith("Deadline mismatch"));
		assertBlockchainIsJustGenesis(blockchain, genesis, config);
	}

	@Test
	@DisplayName("if an added non-genesis block has inconsistent deadline's hashing algorithm, verification rejects it")
	public void deadlineHashingMismatchGetsRejected(@TempDir Path dir) throws NoSuchAlgorithmException, DatabaseException, VerificationException, ClosedDatabaseException, IOException {
		var config = mkConfig(dir);
		var blockchain = mkTestBlockchain(config);
		var hashingForDeadlines = config.getHashingForDeadlines();
		var genesis = Blocks.genesis(LocalDateTime.now(ZoneId.of("UTC")), BigInteger.ONE);
		var deadline = plot.getSmallestDeadline(genesis.getNextDeadlineDescription(config.getHashingForGenerations(), hashingForDeadlines));
		var expected = genesis.getNextBlockDescription(deadline, config.targetBlockCreationTime, config.getHashingForBlocks(), hashingForDeadlines);

		// we replace the expected deadline
		Optional<String> otherAlgorithmName = Stream.of(HashingAlgorithms.TYPES.values()).map(Enum::name).filter(name -> !name.equalsIgnoreCase(deadline.getHashing().getName())).findAny();
		HashingAlgorithm<byte[]> otherAlgorithm = HashingAlgorithms.of(otherAlgorithmName.get(), Function.identity());
		var modifiedDeadline = Deadlines.of(deadline.getProlog(), deadline.getProgressive(), deadline.getValue(),
				deadline.getScoopNumber(), deadline.getData(), otherAlgorithm);
		var block = Blocks.of(expected.getHeight(), expected.getPower(), expected.getTotalWaitingTime(), expected.getWeightedWaitingTime(), expected.getAcceleration(),
				modifiedDeadline, expected.getHashOfPreviousBlock());

		assertTrue(blockchain.add(genesis));
		VerificationException e = assertThrows(VerificationException.class, () -> blockchain.add(block));
		assertTrue(e.getMessage().startsWith("Deadline mismatch"));
		assertBlockchainIsJustGenesis(blockchain, genesis, config);
	}

	@Test
	@DisplayName("if an added non-genesis block has the wrong deadline's prolog chain identifier, verification rejects it")
	public void deadlinePrologChainIdMismatchGetsRejected(@TempDir Path dir) throws NoSuchAlgorithmException, DatabaseException, VerificationException, ClosedDatabaseException, IOException, InvalidKeyException {
		var config = mkConfig(dir);
		var blockchain = mkTestBlockchain(config);
		var hashingForDeadlines = config.getHashingForDeadlines();
		var genesis = Blocks.genesis(LocalDateTime.now(ZoneId.of("UTC")), BigInteger.ONE);
		var deadline = plot.getSmallestDeadline(genesis.getNextDeadlineDescription(config.getHashingForGenerations(), hashingForDeadlines));
		var expected = genesis.getNextBlockDescription(deadline, config.targetBlockCreationTime, config.getHashingForBlocks(), hashingForDeadlines);

		// we create a different prolog
		var modifiedProlog = Prologs.of(prolog.getChainId() + "+", prolog.getNodePublicKey(), prolog.getPlotPublicKey(), prolog.getExtra());
		var modifiedDeadline = Deadlines.of(modifiedProlog, deadline.getProgressive(), deadline.getValue(),
				deadline.getScoopNumber(), deadline.getData(), deadline.getHashing());
		var block = Blocks.of(expected.getHeight(), expected.getPower(), expected.getTotalWaitingTime(), expected.getWeightedWaitingTime(), expected.getAcceleration(),
				modifiedDeadline, expected.getHashOfPreviousBlock());

		assertTrue(blockchain.add(genesis));
		VerificationException e = assertThrows(VerificationException.class, () -> blockchain.add(block));
		assertTrue(e.getMessage().startsWith("Deadline prolog's chainId mismatch"));
		assertBlockchainIsJustGenesis(blockchain, genesis, config);
	}

	@Test
	@DisplayName("if an added non-genesis block has a wrong deadline's prolog extra, verification rejects it")
	public void deadlineInvalidPrologExtraGetsRejected(@TempDir Path dir) throws NoSuchAlgorithmException, DatabaseException, VerificationException, ClosedDatabaseException, IOException, InvalidKeyException {
		var config = mkConfig(dir);
		var application = mock(Application.class);
		when(application.prologExtraIsValid(any())).thenReturn(false);
		var blockchain = mkTestBlockchain(config, application);
		var hashingForDeadlines = config.getHashingForDeadlines();
		var genesis = Blocks.genesis(LocalDateTime.now(ZoneId.of("UTC")), BigInteger.ONE);
		var deadline = plot.getSmallestDeadline(genesis.getNextDeadlineDescription(config.getHashingForGenerations(), hashingForDeadlines));
		var expected = genesis.getNextBlockDescription(deadline, config.targetBlockCreationTime, config.getHashingForBlocks(), hashingForDeadlines);

		assertTrue(blockchain.add(genesis));
		VerificationException e = assertThrows(VerificationException.class, () -> blockchain.add(expected));
		assertTrue(e.getMessage().startsWith("Invalid deadline prolog's extra"));
		assertBlockchainIsJustGenesis(blockchain, genesis, config);
	}

	@Test
	@DisplayName("if an added non-genesis block has an invalid deadline progressive, verification rejects it")
	public void invalidDeadlineProgressiveGetsRejected(@TempDir Path dir) throws NoSuchAlgorithmException, DatabaseException, VerificationException, ClosedDatabaseException, IOException {
		var config = mkConfig(dir);
		var blockchain = mkTestBlockchain(config);
		var hashingForDeadlines = config.getHashingForDeadlines();
		var genesis = Blocks.genesis(LocalDateTime.now(ZoneId.of("UTC")), BigInteger.ONE);
		var deadline = plot.getSmallestDeadline(genesis.getNextDeadlineDescription(config.getHashingForGenerations(), hashingForDeadlines));
		var expected = genesis.getNextBlockDescription(deadline, config.targetBlockCreationTime, config.getHashingForBlocks(), hashingForDeadlines);

		// we make the deadline invalid by changing its progressive
		var modifiedDeadline = Deadlines.of(deadline.getProlog(), deadline.getProgressive() + 1, deadline.getValue(),
				deadline.getScoopNumber(), deadline.getData(), deadline.getHashing());
		var block = Blocks.of(expected.getHeight(), expected.getPower(), expected.getTotalWaitingTime(), expected.getWeightedWaitingTime(), expected.getAcceleration(),
				modifiedDeadline, expected.getHashOfPreviousBlock());

		assertTrue(blockchain.add(genesis));
		VerificationException e = assertThrows(VerificationException.class, () -> blockchain.add(block));
		assertTrue(e.getMessage().startsWith("Invalid deadline"));
		assertBlockchainIsJustGenesis(blockchain, genesis, config);
	}

	@Test
	@DisplayName("if an added non-genesis block has an invalid deadline value, verification rejects it")
	public void invalidDeadlineValueGetsRejected(@TempDir Path dir) throws NoSuchAlgorithmException, DatabaseException, VerificationException, ClosedDatabaseException, IOException {
		var config = mkConfig(dir);
		var blockchain = mkTestBlockchain(config);
		var hashingForDeadlines = config.getHashingForDeadlines();
		var genesis = Blocks.genesis(LocalDateTime.now(ZoneId.of("UTC")), BigInteger.ONE);
		var deadline = plot.getSmallestDeadline(genesis.getNextDeadlineDescription(config.getHashingForGenerations(), hashingForDeadlines));
		var expected = genesis.getNextBlockDescription(deadline, config.targetBlockCreationTime, config.getHashingForBlocks(), hashingForDeadlines);

		// we make the deadline invalid by changing its value (it is not empty since it is a hash)
		var value = deadline.getValue();
		value[0]++;
		var modifiedDeadline = Deadlines.of(deadline.getProlog(), deadline.getProgressive(), value,
				deadline.getScoopNumber(), deadline.getData(), deadline.getHashing());
		var block = Blocks.of(expected.getHeight(), expected.getPower(), expected.getTotalWaitingTime(), expected.getWeightedWaitingTime(), expected.getAcceleration(),
				modifiedDeadline, expected.getHashOfPreviousBlock());

		assertTrue(blockchain.add(genesis));
		VerificationException e = assertThrows(VerificationException.class, () -> blockchain.add(block));
		assertTrue(e.getMessage().startsWith("Invalid deadline"));
		assertBlockchainIsJustGenesis(blockchain, genesis, config);
	}

	private static Config mkConfig(Path dir) throws NoSuchAlgorithmException {
		return Config.Builder.defaults()
				.setDir(dir)
				// we effectively disable the time check
				.setBlockMaxTimeInTheFuture(Long.MAX_VALUE)
				.setChainId("octopus")
				.build();
	}

	private static Blockchain mkTestBlockchain(Config config) throws DatabaseException {
		var application = mock(Application.class);
		when(application.prologExtraIsValid(any())).thenReturn(true);

		return mkTestBlockchain(config, application);
	}

	private static Blockchain mkTestBlockchain(Config config, Application application) throws DatabaseException {
		var peers = mock(NodePeers.class);
		doAnswer(returnsFirstArg())
			.when(peers)
			.asNetworkDateTime(any());
	
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

	private void assertBlockchainIsJustGenesis(Blockchain blockchain, GenesisBlock genesis, Config config) throws DatabaseException, ClosedDatabaseException, NoSuchAlgorithmException {
		assertEquals(genesis, blockchain.getGenesis().get());
		assertEquals(genesis, blockchain.getHead().get());
		byte[][] chain = blockchain.getChain(0, 100).toArray(byte[][]::new);
		assertEquals(1, chain.length);
		assertArrayEquals(chain[0], genesis.getHash(config.hashingForBlocks));
	}
}