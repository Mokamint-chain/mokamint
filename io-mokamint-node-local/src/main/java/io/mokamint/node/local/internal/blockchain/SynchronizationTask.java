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

package io.mokamint.node.local.internal.blockchain;

import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.TimeoutException;
import java.util.logging.Logger;

import io.hotmoka.annotations.OnThread;
import io.mokamint.node.api.Block;
import io.mokamint.node.api.ClosedNodeException;
import io.mokamint.node.api.DatabaseException;
import io.mokamint.node.api.GenesisBlock;
import io.mokamint.node.api.NonGenesisBlock;
import io.mokamint.node.api.Peer;
import io.mokamint.node.api.PeerInfo;
import io.mokamint.node.local.internal.LocalNodeImpl;
import io.mokamint.node.local.internal.LocalNodeImpl.Event;
import io.mokamint.node.local.internal.LocalNodeImpl.Task;
import io.mokamint.node.local.internal.NodePeers;
import io.mokamint.node.remote.RemotePublicNode;

/**
 * A task that synchronizes the blockchain from a given block downwards.
 * That is, it asks the peers about a chain from the block towards the genesis
 * block. If that chain is found, it adds it to the blockchain.
 */
public class SynchronizationTask implements Task {

	/**
	 * The node performing the mining.
	 */
	private final LocalNodeImpl node;

	/**
	 * The blockchain of the node.
	 */
	private final Blockchain blockchain;

	/**
	 * The block from which the synchronization starts.
	 */
	private final Block top;

	/**
	 * The hash of {@link #top}, as a hexadecimal string.
	 */
	private final String hexStartHash;

	private final static Logger LOGGER = Logger.getLogger(SynchronizationTask.class.getName());

	/**
	 * Creates a task that synchronizes the blockchain from a given starting block downwards,
	 * towards a block already in the blockchain.
	 * 
	 * @param node the node requesting the synchronization
	 * @param top the block from which synchronization starts
	 */
	public SynchronizationTask(LocalNodeImpl node, Block top) {
		this.node = node;
		this.blockchain = node.getBlockchain();
		this.top = top;
		this.hexStartHash = top.getHexHash(node.getConfig().getHashingForBlocks());
	}

	@Override
	public String logPrefix() {
		return "";
	}

	@Override
	public String toString() {
		return "chain synchronization from block " + hexStartHash;
	}

	@Override @OnThread("tasks")
	public void body() throws NoSuchAlgorithmException, DatabaseException {
		new Run();
	}

	private class Run {

		private final NodePeers peers = node.getPeers();

		/**
		 * The blocks  downloaded from the peers, from the top to a block whose
		 * previous is finally in the blockchain (or to a genesis). Once this
		 * chain is completely downloaded, it gets added to the blockchain,
		 * from its last to its first element.
		 */
		private final List<Block> chain = new ArrayList<>();

		private final Set<Peer> alreadyTried = new HashSet<Peer>();

		private Run() throws DatabaseException, NoSuchAlgorithmException {
			chain.add(top);

			do {
				Block cursor = chain.get(chain.size() - 1);
				if (cursor instanceof GenesisBlock) {
					addChain();
					return;
				}

				byte[] hashOfPreviousBlock = ((NonGenesisBlock) cursor).getHashOfPreviousBlock();
				if (blockchain.containsBlock(hashOfPreviousBlock)) {
					addChain();
					return;
				}

				Optional<Block> previous;

				do {
					Optional<RemotePublicNode> remote = selectNextPeer();
					if (remote.isEmpty()) {
						node.submit(new MissingBlockEvent(cursor.getHexHash(node.getConfig().hashingForBlocks)));
						return;
					}

					try {
						previous = remote.get().getBlock(hashOfPreviousBlock);
					}
					catch (ClosedNodeException | TimeoutException | InterruptedException e) {
						previous = Optional.empty();
					}
				}
				while (previous.isEmpty());

				chain.add(previous.get());
				alreadyTried.clear();
			}
			while (true);
		}

		private Optional<RemotePublicNode> selectNextPeer() {
			return peers.get()
				.filter(PeerInfo::isConnected)
				.map(PeerInfo::getPeer)
				.filter(alreadyTried::add)
				.map(peers::getRemote)
				.flatMap(Optional::stream)
				.findAny();
		}

		private void addChain() throws NoSuchAlgorithmException, DatabaseException {
			for (int pos = chain.size() - 1; pos >= 0; pos--) {
				Block cursor = chain.get(pos);

				try {
					blockchain.add(cursor);
				}
				catch (VerificationException e) {
					node.submit(new VerificationFailedEvent(e.getMessage(), cursor.getHexHash(node.getConfig().hashingForBlocks)));
					return;
				}
			}
		}
	}

	public static class MissingBlockEvent implements Event {

		/**
		 * The hexadecimal hash of the block whose previous could not be downloaded from the peers.
		 */
		public final String hexHashOfBlock;

		private MissingBlockEvent(String hexHashOfBlock) {
			this.hexHashOfBlock = hexHashOfBlock;
		}

		@Override
		public void body() throws Exception {}

		@Override
		public String toString() {
			return "synchronization failed at block " + hexHashOfBlock + ": cannot download its previous block";
		}

		@Override
		public String logPrefix() {
			return "";
		}
	}

	public static class VerificationFailedEvent implements Event {

		/**
		 * A description of why verification failed.
		 */
		public final String message;

		/**
		 * The hash of the block whose verification failed.
		 */
		public final String hexHashOfBlock;

		private VerificationFailedEvent(String message, String hexHashOfBlock) {
			this.message = message;
			this.hexHashOfBlock = hexHashOfBlock;
		}

		@Override
		public void body() throws Exception {}

		@Override
		public String toString() {
			return "synchronization failed at block " + hexHashOfBlock + ": " + message;
		}

		@Override
		public String logPrefix() {
			return "";
		}
	}
}