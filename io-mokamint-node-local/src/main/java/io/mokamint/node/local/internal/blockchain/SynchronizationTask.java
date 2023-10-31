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

import static io.hotmoka.exceptions.CheckRunnable.check;
import static io.hotmoka.exceptions.UncheckConsumer.uncheck;

import java.io.IOException;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicReferenceArray;
import java.util.logging.Level;
import java.util.logging.Logger;

import io.hotmoka.crypto.api.HashingAlgorithm;
import io.mokamint.node.api.Block;
import io.mokamint.node.api.Chain;
import io.mokamint.node.api.ClosedNodeException;
import io.mokamint.node.api.DatabaseException;
import io.mokamint.node.api.Peer;
import io.mokamint.node.api.PeerInfo;
import io.mokamint.node.local.internal.ClosedDatabaseException;
import io.mokamint.node.local.internal.LocalNodeImpl;
import io.mokamint.node.local.internal.LocalNodeImpl.Task;
import io.mokamint.node.remote.api.RemotePublicNode;
import io.mokamint.node.local.internal.NodePeers;

/**
 * A task that synchronizes the blockchain from the peers.
 * That is, it asks the peers about their best chain from the genesis to the head
 * and downloads the blocks in that chain, exploiting parallelism as much as possible.
 */
public class SynchronizationTask implements Task {

	/**
	 * The node performing the mining.
	 */
	private final LocalNodeImpl node;

	/**
	 * The height of the chain from where synchronization is applied.
	 */
	private final long initialHeight;

	private final static Logger LOGGER = Logger.getLogger(SynchronizationTask.class.getName());

	/**
	 * Creates a task that synchronizes the blockchain from the peers.
	 * 
	 * @param node the node for which synchronization is performed
	 * @param initialHeight the height of the blockchain from where synchronization is applied
	 */
	SynchronizationTask(LocalNodeImpl node, long initialHeight) {
		this.node = node;
		this.initialHeight = initialHeight;
	}

	@Override
	public String logPrefix() {
		return "sync: ";
	}

	@Override
	public String toString() {
		return "chain synchronization from the peers";
	}

	@Override
	public void body() throws NoSuchAlgorithmException, DatabaseException, ClosedDatabaseException, IOException, InterruptedException {
		var blockchain = node.getBlockchain();

		if (blockchain.tryToAcquireSynchronizationLock()) {
			try {
				new Run();
			}
			finally {
				blockchain.releaseSynchronizationLock();
				// after synchronization, we let the blockchain start to mine its blocks
				blockchain.scheduleMining();
			}
		}
	}

	private class Run {

		/**
		 * The peers of the node.
		 */
		private final NodePeers peers = node.getPeers();

		/**
		 * The blockchain of the node.
		 */
		private final Blockchain blockchain = node.getBlockchain();

		/**
		 * The hashing algorithm used for the blocks.
		 */
		private final HashingAlgorithm hashingForBlocks = node.getConfig().getHashingForBlocks();

		/**
		 * The peers that have been discarded so far during this synchronization, since
		 * for instance they timed out or provided illegal blocks.
		 */
		private final Set<Peer> unusable = ConcurrentHashMap.newKeySet();

		/**
		 * The height of the next block whose hash must be downloaded.
		 */
		private long height = initialHeight;

		/**
		 * The last groups of hashes downloaded, for each peer.
		 */
		private final ConcurrentMap<Peer, byte[][]> groups = new ConcurrentHashMap<>();

		/**
		 * The group in {@link #groups} that has been selected as more
		 * reliable chain, because the most peers agree on its hashes.
		 */
		private byte[][] chosenGroup;

		/**
		 * The downloaded blocks, whose hashes are in {@link #chosenGroup}.
		 */
		private AtomicReferenceArray<Block> blocks;

		/**
		 * Semaphores used to avoid having two peers downloading the same block.
		 */
		private Semaphore[] semaphores;

		private final static int GROUP_SIZE = 500;

		private Run() throws DatabaseException, NoSuchAlgorithmException, ClosedDatabaseException, IOException, InterruptedException {
			do {
				if (!downloadNextGroups()) {
					LOGGER.info(logPrefix() + "synchronization stops here since the peers do not provide more block hashes to download");
					break;
				}

				chooseMostReliableGroup();
				downloadBlocks();

				if (!addBlocksToBlockchain()) {
					LOGGER.info(logPrefix() + "synchronization stops here since nomore verifiable blocks can be downloaded");
					break;
				}

				keepOnlyPeersAgreeingOnChosenGroup();

				// -1 is used in order the link the next group with the previous one:
				// they must coincide for the first (respectively, last) block hash
				height += GROUP_SIZE - 1;
			}
			while (chosenGroup.length == GROUP_SIZE);
		}

		/**
		 * Downloads the next group of hashes with each available peer.
		 * 
		 * @return true if and only if at least a group could be downloaded,
		 *         from at least one peer; if false, synchronization must stop here
		 * @throws InterruptedException if the execution has been interrupted
		 * @throws DatabaseException if the database of {@link SynchronizationTask#node} is corrupted
		 * @throws IOException if an I/O error occurs
		 * @throws ClosedDatabaseException if the database of the node is closed
		 */
		private boolean downloadNextGroups() throws InterruptedException, DatabaseException, ClosedDatabaseException, IOException {
			LOGGER.info(logPrefix() + "downloading the hashes of the blocks at height [" + height + ", " + (height + GROUP_SIZE) + ")");

			groups.clear();

			check(InterruptedException.class, DatabaseException.class, ClosedDatabaseException.class, IOException.class, () -> {
				peers.get().parallel()
					.filter(PeerInfo::isConnected)
					.map(PeerInfo::getPeer)
					.filter(peer -> !unusable.contains(peer))
					.forEach(uncheck(this::downloadNextGroup));
			});

			return !groups.isEmpty();
		}

		/**
		 * Download, into the {@link #groups} map, the next group of hashes with the given peer.
		 * 
		 * @param peer the peer
		 * @throws InterruptedException if the execution has been interrupted
		 * @throws DatabaseException if the database of {@link SynchronizationTask#node} is corrupted
		 * @throws ClosedDatabaseException if the database is already closed
		 * @throws IOException if an I/O error occurs
		 */
		private void downloadNextGroup(Peer peer) throws InterruptedException, DatabaseException, ClosedDatabaseException, IOException {
			Optional<RemotePublicNode> maybeRemote = peers.getRemote(peer);
			if (maybeRemote.isEmpty())
				return;

			var remote = maybeRemote.get();
			try {
				Chain chain = remote.getChain(height, GROUP_SIZE);
				var hashes = chain.getHashes().toArray(byte[][]::new);

				// if a peer sends inconsistent information, we ban it
				if (hashes.length > GROUP_SIZE)
					markAsMisbehaving(peer);
				else if (groupIsUseless(hashes))
					unusable.add(peer);
				else
					groups.put(peer, hashes);
			}
			catch (DatabaseException e) {
				// it is the database of the peer that is corrupted, not that of {@code node}
				markAsMisbehaving(peer);
			}
			catch (TimeoutException | ClosedNodeException e) {
				markAsUnreachable(peer);
			}
		}

		/**
		 * Determines if the given group of hashes can be discarded since it does not firt with some
		 * expected constraints.
		 * 
		 * @param hashes the group of hashes
		 * @return true if and only if it can be discarded
		 * @throws DatabaseException if the database is corrupted
		 * @throws ClosedDatabaseException if the database is already closed
		 */
		private boolean groupIsUseless(byte[][] hashes) throws DatabaseException, ClosedDatabaseException {
			Optional<byte[]> genesisHash;

			// the first hash must coincide with the last hash of the previous group,
			// or otherwise the peer is cheating or there has been a change in the best chain
			// and we must anyway stop downloading blocks here from this peer
			if (hashes.length > 0 && chosenGroup != null && !Arrays.equals(hashes[0], chosenGroup[chosenGroup.length - 1]))
				return true;
			// if synchronization occurs from the genesis and the genesis of the blockchain is set,
			// then the first hash must be that genesis' hash
			else if (hashes.length > 0 && height == 0L && (genesisHash = node.getBlockchain().getGenesisHash()).isPresent()
					&& !Arrays.equals(hashes[0], genesisHash.get()))
				return true;
			// if synchronization starts from above the genesis, the first hash must be in the blockchain of the node or
			// otherwise the hashes are useless
			else if (hashes.length > 0 && chosenGroup == null && height > 0L && !blockchain.containsBlock(hashes[0]))
				return true;
			else
				return false;
		}

		private void markAsMisbehaving(Peer peer) throws DatabaseException, ClosedDatabaseException, InterruptedException, IOException {
			unusable.add(peer);
			peers.remove(peer);
		}

		private void markAsUnreachable(Peer peer) throws DatabaseException, ClosedDatabaseException, InterruptedException, IOException {
			unusable.add(peer);
			peers.punishBecauseUnreachable(peer);
		}

		/**
		 * Selects the group in {@link #groups} that looks as the most reliable, since the most
		 * peers agree on its hashes.
		 */
		private void chooseMostReliableGroup() {
			var alternatives = new HashSet<byte[][]>(groups.values());

			for (int h = 1; h < GROUP_SIZE && alternatives.size() > 1; h++) {
				Optional<byte[][]> mostFrequent = findMostFrequent(alternatives, h);
				// there might be no alternatives with at least h hashes
				if (mostFrequent.isEmpty())
					break;

				byte[] mostFrequentHash = mostFrequent.get()[h];
				for (byte[][] alternative: new HashSet<>(alternatives))
					if (alternative.length <= h || !Arrays.equals(alternative[h], mostFrequentHash))
						alternatives.remove(alternative);
			}

			// the remaining alternatives actually coincide: just take one
			chosenGroup = alternatives.stream().findAny().get();
		}

		/**
		 * Yields the alternative whose {@code h}'s hash is the most frequent
		 * among the given {@code alternatives}.
		 * 
		 * @param alternatives the alternatives
		 * @param h the index of the compared hash of the alternatives
		 * @return the alternative whose {@code h}'s hash is the most frequent among {@code alternatives};
		 *         this is missing when all alternatives have fewer than {@code h} hashes
		 */
		private Optional<byte[][]> findMostFrequent(Set<byte[][]> alternatives, int h) {
			byte[][] result = null;
			long bestFrequency = 0L;
			for (byte[][] alternative: alternatives) {
				long frequency = computeFrequency(alternative, alternatives, h);
				if (frequency > bestFrequency) {
					bestFrequency = frequency;
					result = alternative;
				}
			}

			return Optional.ofNullable(result);
		}

		/**
		 * Counts how many {@code alternatives} have their {@code h}'s hash coinciding
		 * with that of {@code alternative}.
		 * 
		 * @param alternative the reference alternative
		 * @param alternatives the alternatives
		 * @param h the height of the counted hash
		 * @return the count; this is 0 if all alternatives have fewer than {@code h} hashes
		 */
		private long computeFrequency(byte[][] alternative, Set<byte[][]> alternatives, int h) {
			return alternatives.stream()
				.filter(hashes -> hashes.length > h)
				.map(hashes -> hashes[h])
				.filter(hash -> Arrays.equals(hash, alternative[h]))
				.count();
		}

		/**
		 * Downloads as many blocks as possible whose hashes are in {@link #chosenGroup},
		 * by using the peers whose group agrees on such hashes, in parallel. Some block
		 * might no be downloaded if all peers time out or no peer contains that block.
		 * 
		 * @throws DatabaseException if the database of {@link SynchronizationTask#node} is corrupted
		 * @throws InterruptedException if the computation has been interrupted
		 * @throws IOException if an I/O error occurs
		 * @throws ClosedDatabaseException if the database of the node is closed
		 */
		private void downloadBlocks() throws InterruptedException, DatabaseException, ClosedDatabaseException, IOException {
			blocks = new AtomicReferenceArray<>(chosenGroup.length);
			semaphores = new Semaphore[chosenGroup.length];
			Arrays.setAll(semaphores, _index -> new Semaphore(1));

			LOGGER.info(logPrefix() + "downloading the blocks at height [" + height + ", " + (height + chosenGroup.length) + ")");

			check(InterruptedException.class, DatabaseException.class, ClosedDatabaseException.class, IOException.class, () -> {
				peers.get().parallel()
					.filter(PeerInfo::isConnected)
					.map(PeerInfo::getPeer)
					.filter(peer -> !unusable.contains(peer))
					.forEach(uncheck(this::downloadBlocks));
			});
		}

		private void downloadBlocks(Peer peer) throws DatabaseException, ClosedDatabaseException, InterruptedException, IOException {
			byte[][] ownGroup = groups.get(peer);
			if (ownGroup != null) {
				var alreadyTried = new boolean[chosenGroup.length];

				// we try twice: the second time to help peers that are trying to download something
				for (int time = 1; time <= 2; time++) {
					for (int h = chosenGroup.length - 1; h >= 0; h--)
						if (canDownload(peer, h, ownGroup, alreadyTried))
							if (time == 2 || semaphores[h].tryAcquire()) {
								try {
									alreadyTried[h] = true;
									tryToDownloadBlock(peer, h);
								}
								finally {
									if (time == 1)
										semaphores[h].release();
								}
							}
				}
			}
		}

		/**
		 * Determines if the given peer could be used to download the block with the {@code h}th hash in {@link #chosenGroup}.
		 * 
		 * @param peer the peer
		 * @param h the index of the hash
		 * @param ownGroup the group of hashes for the peer
		 * @param alreadyTried information about which hashes have already been tried with this same peer
		 * @return true if and only if it is sensible to use {@code peer} to download the block
		 * @throws DatabaseException of the database of the node is corrupted
		 * @throws ClosedDatabaseException if the database is already closed
		 */
		private boolean canDownload(Peer peer, int h, byte[][] ownGroup, boolean[] alreadyTried) throws DatabaseException, ClosedDatabaseException {
			return !unusable.contains(peer) && !alreadyTried[h] && ownGroup.length > h && Arrays.equals(ownGroup[h], chosenGroup[h]) && !blockchain.containsBlock(chosenGroup[h]) && blocks.get(h) == null;
		}

		/**
		 * Tries to download the block with the {@code h}th hash in {@link #chosenGroup},
		 * from the given peer.
		 * 
		 * @param peer the peer
		 * @param h the height of the hash
		 * @throws InterruptedException if the executed was interrupted
		 * @throws DatabaseException if the database of the node is corrupted
		 * @throws ClosedDatabaseException if the database of the node is already closed
		 * @throws IOException if an I/O error occurs
		 */
		private void tryToDownloadBlock(Peer peer, int h) throws InterruptedException, DatabaseException, ClosedDatabaseException, IOException {
			var maybeRemote = peers.getRemote(peer);
			if (maybeRemote.isEmpty())
				unusable.add(peer);
			else {
				var remote = maybeRemote.get();
				Optional<Block> maybeBlock;

				try {
					maybeBlock = remote.getBlock(chosenGroup[h]);
				}
				catch (DatabaseException | NoSuchAlgorithmException e) {
					markAsMisbehaving(peer);
					return;
				}
				catch (TimeoutException | ClosedNodeException e) {
					markAsUnreachable(peer);
					return;
				}

				if (maybeBlock.isPresent()) {
					Block block = maybeBlock.get();
					if (!Arrays.equals(chosenGroup[h], block.getHash(hashingForBlocks)))
						// the peer answered with a block with the wrong hash!
						markAsMisbehaving(peer);
					else
						blocks.set(h, block);
				}
			}
		}

		/**
		 * Adds the {@link #blocks} to the blockchain, stopping at the first missing block
		 * or at the first block that cannot be verified.
		 * 
		 * @return true if and only if no block was missing and all blocks could be
		 *         successfully verified; if false, synchronization must stop here
		 * @throws DatabaseException if the database of the node is corrupted
		 * @throws NoSuchAlgorithmException if some block in the database of the node uses an unknown hashing algorithm
		 * @throws ClosedDatabaseException if the database is already closed
		 */
		private boolean addBlocksToBlockchain() throws DatabaseException, NoSuchAlgorithmException, ClosedDatabaseException {
			for (int h = 0; h < chosenGroup.length; h++)
				if (!blockchain.containsBlock(chosenGroup[h])) {
					Block block = blocks.get(h);
					if (block == null)
						return false;

					try {
						blockchain.add(block);
					}
					catch (VerificationException e) {
						LOGGER.log(Level.SEVERE, "verification of block " + block.getHexHash(hashingForBlocks) + " failed: " + e.getMessage());
						return false;
					}
				}

			return true;
		}

		/**
		 * Puts in the {@link #unusable} set all peers that downloaded a group
		 * different from {@link #chosenGroup}: in any case, their subsequent groups are more
		 * a less reliable history and won't be downloaded.
		 */
		private void keepOnlyPeersAgreeingOnChosenGroup() {
			for (var entry: groups.entrySet())
				if (!Arrays.deepEquals(chosenGroup, entry.getValue()))
					unusable.add(entry.getKey());
		}
	}
}