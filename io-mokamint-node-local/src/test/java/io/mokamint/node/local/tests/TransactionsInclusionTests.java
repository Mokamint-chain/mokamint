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

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Path;
import java.security.InvalidKeyException;
import java.security.KeyPair;
import java.security.NoSuchAlgorithmException;
import java.security.SignatureException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Optional;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeoutException;
import java.util.stream.IntStream;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

import io.hotmoka.exceptions.CheckSupplier;
import io.hotmoka.exceptions.UncheckFunction;
import io.hotmoka.testing.AbstractLoggedTests;
import io.mokamint.application.api.Application;
import io.mokamint.miner.local.LocalMiners;
import io.mokamint.node.Peers;
import io.mokamint.node.Transactions;
import io.mokamint.node.api.Block;
import io.mokamint.node.api.NodeException;
import io.mokamint.node.api.NonGenesisBlock;
import io.mokamint.node.api.Peer;
import io.mokamint.node.api.PeerInfo;
import io.mokamint.node.api.PeerRejectedException;
import io.mokamint.node.api.Transaction;
import io.mokamint.node.api.TransactionRejectedException;
import io.mokamint.node.local.AbstractLocalNode;
import io.mokamint.node.local.ApplicationTimeoutException;
import io.mokamint.node.local.LocalNodeConfigBuilders;
import io.mokamint.node.local.api.LocalNode;
import io.mokamint.node.local.api.LocalNodeConfig;
import io.mokamint.node.service.PublicNodeServices;
import io.mokamint.node.service.api.PublicNodeService;
import io.mokamint.nonce.Prologs;
import io.mokamint.plotter.PlotAndKeyPairs;
import io.mokamint.plotter.Plots;
import io.mokamint.plotter.api.Plot;
import io.mokamint.plotter.api.PlotException;
import io.mokamint.plotter.api.WrongKeyException;
import jakarta.websocket.DeploymentException;

/**
 * Tests about the inclusion of transactions in blockchain.
 */
public class TransactionsInclusionTests extends AbstractLoggedTests {

	/**
	 * The application of the node used for testing.
	 */
	private static Application app;

	@BeforeAll
	public static void beforeAll(@TempDir Path plotDir) throws Exception {
		app = mock(Application.class);
		when(app.checkPrologExtra(any())).thenReturn(true);
		when(app.getInitialStateId()).thenReturn(new byte[] { 1, 2, 3 });
		doNothing().when(app).checkTransaction(any());
		doNothing().when(app).deliverTransaction(anyInt(), any());
		when(app.endBlock(anyInt(), any())).thenReturn(new byte[] { 13, 17, 42 });
	}

	private LocalNodeConfig mkConfig(Path chainDir) throws NoSuchAlgorithmException {
		return LocalNodeConfigBuilders.defaults()
			.setDir(chainDir)
			.setChainId("octopus")
			.setTargetBlockCreationTime(1_000)
			.build();
	}

	private static class NodeWithLocalMiner extends AbstractLocalNode {
		private final Plot plot;
		private final KeyPair plotKeys;

		private NodeWithLocalMiner(LocalNodeConfig config, boolean init) throws IOException, InterruptedException, InvalidKeyException, NodeException, ApplicationTimeoutException, WrongKeyException {
			super(config, config.getSignatureForBlocks().getKeyPair(), app, init);

			this.plotKeys = config.getSignatureForDeadlines().getKeyPair();
			var prolog = Prologs.of(config.getChainId(), config.getSignatureForBlocks(), getKeys().getPublic(),
					config.getSignatureForDeadlines(), plotKeys.getPublic(), new byte[0]);
			long start = 65536L;
			long length = new Random().nextInt(50, 200);
			this.plot = Plots.create(config.getDir().resolve("plot.plot"), prolog, start, length, config.getHashingForDeadlines(), __ -> {});
			add(LocalMiners.of(PlotAndKeyPairs.of(plot, plotKeys)));
		}

		@Override
		public void close() throws NodeException {
			super.close();

			try {
				plot.close();
			}
			catch (PlotException e) {
				throw new NodeException("Could not close the plot", e);
			}
		}
	}

	@Test
	@Timeout(20)
	@DisplayName("transactions added to the mempool get eventually added to the blockchain")
	public void transactionsAddedToMempoolEventuallyReachBlockchain(@TempDir Path chain) throws Exception {
		var allTransactions = new HashSet<Transaction>();
		var random = new Random();
		while (allTransactions.size() < 100) {
			int length = random.nextInt(10, 1000);
			var bytes = new byte[length];
			random.nextBytes(bytes);
			var tx = Transactions.of(bytes);
			allTransactions.add(tx);
		}
		var allIncluded = new Semaphore(0);
		var config = mkConfig(chain);

		class TestNode extends NodeWithLocalMiner {

			private TestNode(LocalNodeConfig config, boolean init) throws IOException, InterruptedException, InvalidKeyException, ApplicationTimeoutException, NodeException, WrongKeyException {
				super(config, init);
			}

			@Override
			protected void onAdded(Block block) {
				super.onAdded(block);

				if (block instanceof NonGenesisBlock ngb) {
					ngb.getTransactions().forEach(allTransactions::remove);
					if (allTransactions.isEmpty())
						allIncluded.release();
				}
			}
		}

		var copy = new ArrayList<>(allTransactions);
		try (var miningNode = new TestNode(config, true)) {
			for (var tx: copy) {
				miningNode.add(tx); // allTransactions has no repetitions => no rejection
				Thread.sleep(10);
			}

			allIncluded.acquire();
		}
	}

	@Test
	@Timeout(200)
	@DisplayName("transactions added to a network get eventually added to the blockchain")
	public void transactionsAddedToNetworkEventuallyReachBlockchain(@TempDir Path dir) throws Exception {
		var allTransactions = mkTransactions();
		final int NUM_NODES = 4;

		class Run {
			class TestNode extends NodeWithLocalMiner {
				private Semaphore seenAll;
				private Set<Transaction> added;

				protected synchronized Semaphore getSeenAll() {
					if (seenAll == null)
						seenAll = new Semaphore(0);

					return seenAll;
				}

				private synchronized Set<Transaction> getAdded() {
					if (added == null)
						added = new HashSet<>();

					return added;
				}

				private TestNode(LocalNodeConfig config, boolean init) throws IOException, InterruptedException, InvalidKeyException, NodeException, ApplicationTimeoutException, WrongKeyException {
					super(config, init);
				}

				@Override
				protected void onAdded(Block block) {
					super.onAdded(block);

					if (block instanceof NonGenesisBlock ngb) {
						synchronized (this) {
							ngb.getTransactions().forEach(getAdded()::add);

							synchronized (allTransactions) {
								if (getAdded().equals(allTransactions))
									getSeenAll().release();
							}
						}
					}
				}
			}

			private final TestNode[] nodes;
			private final PublicNodeService[] services;
			private final Random random = new Random();
			
			private Run() throws InterruptedException, TransactionRejectedException, TimeoutException, IOException, PeerRejectedException, NodeException {
				this.services = new PublicNodeService[NUM_NODES];

				try {
					System.out.println("openNodes");
					this.nodes = openNodes(dir);
					System.out.println("addPeers");
					addPeers();
					System.out.println("addTransactions");
					addTransactions();
					System.out.println("waitUntilAllNodesHaveSeenAllTransactions");
					waitUntilAllNodesHaveSeenAllTransactions();
					for (var node: nodes)
						System.out.println(Arrays.toString(node.getPeerInfos().map(PeerInfo::getPeer).toArray()));
					closeNodes();
				}
				finally {
					// normally, the services get closed with the nodes, but we force their closure
					// anyway, so that, in case of test failure, no service remains open
					// (otherwise, subsequent tests might find some port busy and fail as well)
					closeServices();
				}
			}

			private void waitUntilAllNodesHaveSeenAllTransactions() throws InterruptedException {
				for (TestNode node: nodes)
					node.getSeenAll().acquire();
			}

			private TestNode[] openNodes(Path dir) throws InterruptedException {
				return CheckSupplier.check(InterruptedException.class, () -> 
					IntStream.range(0, NUM_NODES).parallel().mapToObj(Integer::valueOf)
						.map(UncheckFunction.uncheck(num -> mkNode(dir, num))).toArray(TestNode[]::new));
			}

			private void closeNodes() throws InterruptedException, NodeException {
				for (var node: nodes)
					node.close();
			}

			private void closeServices() throws InterruptedException {
				for (var service: services)
					if (service != null)
						service.close();
			}

			private void addTransactions() throws TransactionRejectedException, TimeoutException, InterruptedException, NodeException {
				for (Transaction tx: allTransactions) {
					nodes[random.nextInt(NUM_NODES)].add(tx);
					Thread.sleep(50);
				}
			}

			private void addPeers() throws InterruptedException, TimeoutException, IOException, PeerRejectedException, NodeException {
				for (int pos = 0; pos < nodes.length; pos++)
					nodes[pos].add(getPeer((pos + 1) % NUM_NODES));
			}

			private LocalNode mkNode(Path dir, int num) throws InvalidKeyException, SignatureException, NoSuchAlgorithmException, IOException, InterruptedException, DeploymentException, ApplicationTimeoutException, NodeException, WrongKeyException {
				LocalNode result = new TestNode(mkConfig(dir.resolve("node" + num)), num == 0);

				var uri = getPeer(num).getURI();
				// this service will be closed automatically when the node will get closed
				services[num] = PublicNodeServices.open(result, uri.getPort(), 1800000, 1000, Optional.of(uri));

				return result;
			}

			private Peer getPeer(int num) {
				try {
					return Peers.of(new URI("ws://localhost:" + (8032 + num)));
				}
				catch (URISyntaxException e) {
					throw new RuntimeException("Unexpected exception", e);
				}
			}
		}

		new Run();
	}

	private Set<Transaction> mkTransactions() {
		var allTransactions = new HashSet<Transaction>();
		var random = new Random();
		while (allTransactions.size() < 200) {
			int length = random.nextInt(10, 1000);
			var bytes = new byte[length];
			random.nextBytes(bytes);
			var tx = Transactions.of(bytes);
			allTransactions.add(tx);
		}

		return allTransactions;
	}
}