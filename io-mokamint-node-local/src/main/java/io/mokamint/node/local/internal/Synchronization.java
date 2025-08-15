/*
Copyright 2025 Fausto Spoto

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

package io.mokamint.node.local.internal;

import java.util.Arrays;
import java.util.Comparator;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.IntStream;

import io.hotmoka.annotations.GuardedBy;
import io.hotmoka.crypto.Hex;
import io.mokamint.application.api.ClosedApplicationException;
import io.mokamint.node.api.ApplicationTimeoutException;
import io.mokamint.node.api.Block;
import io.mokamint.node.api.BlockDescription;
import io.mokamint.node.api.ChainPortion;
import io.mokamint.node.api.ClosedNodeException;
import io.mokamint.node.api.Peer;
import io.mokamint.node.api.ClosedPeerException;
import io.mokamint.node.api.PeerInfo;
import io.mokamint.node.api.PortionRejectedException;
import io.mokamint.node.local.api.LocalNodeConfig;
import io.mokamint.node.local.internal.BlockVerification.Mode;

/**
 * The synchronization algorithm of the blockchain. It consists in a pipeline
 * with three stages: blocks are first downloaded from the peers, then undergo
 * partial (non-contextual) verification and finally undergo contextual verification
 * and addition to blockchain, if they pass that verification.
 */
public class Synchronization {

	/**
	 * The node whose blockchain is getting synchronized.
	 */
	private final LocalNodeImpl node;

	/**
	 * The configuration of {@link #node}.
	 */
	private final LocalNodeConfig config;

	/**
	 * The peers of {@link #node}.
	 */
	private final PeersSet peers;

	/**
	 * The tasks of the downloading stage of the pipeline.
	 */
	private final Downloader[] downloaders;

	/**
	 * The tasks of the partial verification stage of the pipeline.
	 */
	private final BlockNonContextualVerifier[] nonContextualVerifiers;

	/**
	 * The tasks of the block addition stage of the pipeline.
	 */
	private final BlockAdder[] blockAdders;

	/**
	 * The queue of blocks that have been downloaded but have not been
	 * completely processed (added or rejected) yet.
	 */
	@GuardedBy("itself")
	private final SortedSet<Block> blocksDownloadedNotYetProcessed = new TreeSet<>(new BlockComparatorByHeight());

	/**
	 * The queue of blocks that have been downloaded and not yet verified.
	 */
	@GuardedBy("itself")
	private final SortedSet<Block> blocksToVerify = new TreeSet<>(new BlockComparatorByHeight());

	/**
	 * The queue of blocks that have been downloaded and partially verified, but still
	 * need to be completely verified and added to blockchain (or rejected).
	 */
	@GuardedBy("itself")
	private final SortedSet<Block> blocksPartiallyVerified = new TreeSet<>(new BlockComparatorByHeight());

	/**
	 * A map from block hash to block with that hash, for the blocks downloaded during synchronization.
	 */
	private final ConcurrentMap<String, Block> hashToBlock = new ConcurrentHashMap<>();

	/**
	 * The size of the groups of block hashes that get downloaded with a single request.
	 */
	private final int synchronizationGroupSize;

	/**
	 * The block height from where synchronization starts.
	 */
	private final long startingHeight;

	/**
	 * The blockchain of the {@link #node}.
	 */
	private final Blockchain blockchain;

	/**
	 * The executors to use to spawn new tasks.
	 */
	private final ExecutorService executors;

	/**
	 * A counter of the block adders that have terminated. When they reach
	 * the total initial amount of block adders, synchronization terminates.
	 */
	private final Semaphore blockAddersHaveTerminated = new Semaphore(0);

	private final static Logger LOGGER = Logger.getLogger(Synchronization.class.getName());

	/**
	 * Performs the synchronization of the blockchain of the given node.
	 * 
	 * @param node the node whose blockchain is synchronized
	 * @param executors the executors to use to spawn new tasks
	 * @throws InterruptedException if the operation gets interrupted
	 * @throws ClosedNodeException if the node is already closed
	 * @throws ClosedDatabaseException if the database is already closed
	 */
	public Synchronization(LocalNodeImpl node, ExecutorService executors) throws InterruptedException, ClosedNodeException, ClosedDatabaseException {
		LOGGER.info("sync: synchronization starts");
		this.node = node;
		this.config = node.getConfig();
		this.peers = node.getPeers();
		this.synchronizationGroupSize = config.getSynchronizationGroupSize();
		this.blockchain = node.getBlockchain();
		this.executors = executors;

		// if the node has been disconnected, it will have constructed its own branch; as long as this is less than 1000 blocks long,
		// the subsequent choice allows synchronization upon reconnection, otherwise the node will not be able to synchronize anymore
		this.startingHeight = Math.max(blockchain.getStartOfNonFrozenPart().map(Block::getDescription).map(BlockDescription::getHeight).orElse(0L), blockchain.getHeightOfHead().orElse(0L) - 1000L);
		this.downloaders = mkBlockDownloaders();
		this.nonContextualVerifiers = mkNonContextualVerifiers();
		this.blockAdders = mkBlockAdders();
		startBlockAdders();
		startNonContextualVerifiers();
		startBlockDownloaders();
		waitUntilBlockAddersTerminate();
		LOGGER.info("sync: synchronization stops");
	}

	private void waitUntilBlockAddersTerminate() throws InterruptedException {
		blockAddersHaveTerminated.acquire(blockAdders.length);
	}

	private void startBlockAdders() {
		for (var adder: blockAdders)
			executors.submit(adder::run);
	}

	private void startBlockDownloaders() {
		for (var downloader: downloaders)
			executors.submit(downloader::run);
	}

	private void startNonContextualVerifiers() {
		for (var verifier: nonContextualVerifiers)
			executors.submit(verifier::run);
	}

	private Downloader[] mkBlockDownloaders() {
		return peers.get()
				.filter(PeerInfo::isConnected)
				.map(PeerInfo::getPeer)
				.map(Downloader::new)
				.toArray(Downloader[]::new);
	}

	private BlockNonContextualVerifier[] mkNonContextualVerifiers() {
		return IntStream.range(0, 1 + Runtime.getRuntime().availableProcessors() / 2)
			.mapToObj(BlockNonContextualVerifier::new)
			.toArray(BlockNonContextualVerifier[]::new);
	}

	private BlockAdder[] mkBlockAdders() {
		return IntStream.range(0, 1 + Runtime.getRuntime().availableProcessors() / 2)
			.mapToObj(BlockAdder::new)
			.toArray(BlockAdder[]::new);
	}

	/**
	 * Takes node that the given downloader wants to download a given block.
	 * It takes note of that request, so that the peer of the downloader can
	 * later be banned if the block turns out to be illegal.
	 * 
	 * @param blockHash the hash of the block requested to download
	 * @param downloader the downloader that requests to download the block
	 * @return true if and only if some downloader already requested
	 *         to download that block; this can be checked to avoid downloading
	 *         the same block with the same downloader; instead, if the result is true
	 *         it is better to use the downloader to download some other block
	 */
	private synchronized boolean requestToDownload(String blockHash, Downloader downloader) {
		// synchronization of this method is needed to avoid assigning the same block to download twice

		for (Downloader other: downloaders)
			if (other.blocksRequestedByThis.contains(blockHash)) {
				downloader.blocksRequestedByThis.add(blockHash);
				return false;
			}

		downloader.blocksRequestedByThis.add(blockHash);
		return true;
	}

	/**
	 * Removes downloading data that refer to old blocks, that won't be needed anymore:
	 * this avoid the explosion of their containers.
	 */
	private void cleanUpDownloaders() {
		long minHeight = minimalHeightStillToProcess();

		for (var entry: hashToBlock.entrySet()) {
			Block block = entry.getValue();

			if (block.getDescription().getHeight() < minHeight) {
				String blockHash = entry.getKey();
				hashToBlock.remove(blockHash);

				for (var downloader: downloaders) {
					downloader.blocksAddedToProcessByThis.remove(block);
					downloader.blocksRequestedByThis.remove(blockHash);
				}
			}
		}
	}

	private boolean allDownloadersHaveTerminated() {
		for (Downloader downloader: downloaders)
			if (!downloader.terminated)
				return false;

		return true;
	}

	/**
	 * Takes note that the given block must be added to those to process.
	 * 
	 * @param block the block
	 * @param downloader the downloader of the block
	 * @throws InterruptedException if the operation gets interrupted
	 */
	private void addToProcess(Block block, Downloader downloader) throws InterruptedException {
		downloader.queueHasSpace.acquire();
		downloader.blocksAddedToProcessByThis.add(block);

		synchronized (blocksDownloadedNotYetProcessed) {
			blocksDownloadedNotYetProcessed.add(block);
		}

		synchronized (blocksToVerify) {
			blocksToVerify.add(block);
			blocksToVerify.notifyAll();
		}
	}

	/**
	 * A counter of the times {@link #markAsProcessed(Block)} gets called.
	 */
	private final AtomicInteger processingCounter = new AtomicInteger();

	private void markAsProcessed(Block block) {
		synchronized (blocksDownloadedNotYetProcessed) {
			blocksDownloadedNotYetProcessed.remove(block);
		}

		for (var downloader: downloaders)
			if (downloader.blocksAddedToProcessByThis.contains(block))
				downloader.queueHasSpace.release();

		// every 1000 removals, we clean-up of the data structures used for downloading,
		// by removing elements that won't be needed anymore
		if (processingCounter.incrementAndGet() % 1000 == 0)
			cleanUpDownloaders();
	}

	/**
	 * Yields a lower bound of the height of blocks that have been downloaded but have not been completely
	 * processed yet (added to blockchain or rejected).
	 * 
	 * @return the lower bound
	 */
	private long minimalHeightStillToProcess() {
		long result = Long.MAX_VALUE;

		// first we use the thresholds of the downloaders
		for (var downloader: downloaders)
			if (!downloader.terminated)
				result = Math.min(result, downloader.getHeight());

		// then we consider that some block might still be in the pipeline
		synchronized (blocksDownloadedNotYetProcessed) {
			if (!blocksDownloadedNotYetProcessed.isEmpty())
				result = Math.min(result, blocksDownloadedNotYetProcessed.first().getDescription().getHeight());
		}

		return result;
	}

	private boolean allNonContextualVerifiersHaveTerminated() {
		for (BlockNonContextualVerifier verifier: nonContextualVerifiers)
			if (!verifier.terminated)
				return false;

		return true;
	}

	/**
	 * Takes note that a block was illegal and consequently who requested to download that block must be banned.
	 * 
	 * @param blockHash the hash of the illegal block
	 */
	private void banDownloadersOf(String blockHash) throws ClosedDatabaseException {
		for (var downloader: downloaders)
			if (!downloader.terminated && downloader.blocksRequestedByThis.contains(blockHash))
				downloader.banOnIllegalBlock();
	}

	/**
	 * A block downloader task, implementing the first stage of the pipeline.
	 */
	private class Downloader {

		/**
		 * The peer used by this downloader to download blocks.
		 */
		private final Peer peer;

		private volatile boolean terminated;

		@GuardedBy("this")
		private long height;

		private final Set<String> blocksRequestedByThis = ConcurrentHashMap.newKeySet();
		private final Set<Block> blocksAddedToProcessByThis = ConcurrentHashMap.newKeySet();

		/**
		 * This limits the number of blocks that this downloader has loaded but have not been
		 * fully processed yet (added to blockchain or rejected): the goal is to avoid
		 * the explosion of the queues.
		 */
		private final Semaphore queueHasSpace = new Semaphore(synchronizationGroupSize * 2);

		private Downloader(Peer peer) {
			this.peer = peer;
			this.height = startingHeight;
		}

		private void run() {
			try {
				Optional<byte[]> lastHashOfPreviousGroup = Optional.empty();

				while (!terminated) {
					Optional<byte[][]> maybeHashes = downloadNextGroupOfBlockHashes(lastHashOfPreviousGroup);
					byte[][] hashes;

					if (maybeHashes.isEmpty() || (hashes = maybeHashes.get()).length == 0)
						break;

					if (!downloadNextGroupOfBlocks(hashes))
						return;

					if (hashes.length < synchronizationGroupSize) // if the group of hashes was not full, we stop
						break;

					lastHashOfPreviousGroup = Optional.of(hashes[hashes.length - 1]);

					synchronized (this) {
						height += synchronizationGroupSize - 1;
					}
				}

				LOGGER.info("sync: block downloading from " + peer + " stops because no useful hashes have been provided anymore by the peer");
			}
			catch (ClosedPeerException e) {
				LOGGER.warning("sync: block downloading from " + peer + " stops because the peer is already closed: " + e.getMessage());
			}
			catch (PortionRejectedException e) {
				LOGGER.warning("sync: block downloading from " + peer + " stops because the peer rejected a request for fetching block hashes: " + e.getMessage());
			}
			catch (PeerTimeoutException e) {
				LOGGER.warning("sync: block downloading from " + peer + " stops because the peer is not answering: " + e.getMessage());
			}
			catch (ClosedDatabaseException e) {
				LOGGER.warning("sync: block downloading from " + peer + " stops because the database has been closed: " + e.getMessage());
			}
			catch (InterruptedException e) {
				Thread.currentThread().interrupt();
				LOGGER.warning("sync: block downloading from " + peer + " has been interrupted");
			}
			catch (RuntimeException e) {
				LOGGER.log(Level.SEVERE, "sync: block downloading from " + peer + " stops because the node is misbehaving", e);
			}
			finally {
				terminated = true;

				// if some block verifier was waiting for blocks to verify, we signal that we terminated
				// because it might decide to terminate as well
				synchronized (blocksToVerify) {
					blocksToVerify.notifyAll();
				}
			}
		}

		/**
		 * Bans the peer used by this downloader, because it downloaded an illegal block.
		 * It removes all blocks downloaded by this downloader but not yet processed.
		 */
		private void banOnIllegalBlock() throws ClosedDatabaseException {
			terminated = true;
			peers.ban(peer);

			// the blocks downloaded by this downloader will be illegal, hence
			// it's more efficient to remove them from those added to process by the other peers
			Set<Block> toRemove = new HashSet<>(blocksAddedToProcessByThis);
			for (var downloader: downloaders)
				if (downloader != this)
					toRemove.removeAll(downloader.blocksAddedToProcessByThis);

			synchronized (blocksDownloadedNotYetProcessed) {
				blocksDownloadedNotYetProcessed.removeAll(toRemove);
			}

			synchronized (blocksToVerify) {
				blocksToVerify.removeAll(toRemove);
			}

			synchronized (blocksPartiallyVerified) {
				blocksPartiallyVerified.removeAll(toRemove);
			}
		}

		private synchronized long getHeight() {
			return height;
		}

		/**
		 * Downloads the next group of blocks.
		 * 
		 * @param hashes the hashes of the blocks to download
		 * @return true if and only if all blocks could have been downloaded
		 * @throws InterruptedException if the current thread gets interrupted
		 * @throws ClosedPeerException if the peer is misbehaving
		 * @throws PeerTimeoutException if the peer does not answer on time
		 * @throws ClosedDatabaseException if the database is already closed
		 */
		private boolean downloadNextGroupOfBlocks(byte[][] hashes) throws InterruptedException, ClosedPeerException, PeerTimeoutException, ClosedDatabaseException {
			var blocks = new Block[hashes.length];

			// in a first iteration, we download blocks only if no other downloader is taking care of them,
			// in order to allow concurrent downloading from many peers; the first hash is discarded
			// for the non-initial group, since it is the same that ended the previous block
			for (int pos = height == startingHeight ? 0 : 1; pos < hashes.length; pos++) {
				if (terminated)
					return false;

				var hash = hashes[pos];
				var blockHash = Hex.toHexString(hash);
				if (requestToDownload(blockHash, this) && downloadBlock(blocks, pos, hash, blockHash).isEmpty())
					return false;
			}

			// in a second iteration, we download all blocks that still need to be downloaded
			for (int pos = height == startingHeight ? 0 : 1; pos < hashes.length; pos++) {
				if (terminated)
					return false;
				else if (blocks[pos] == null) {
					var hash = hashes[pos];
					String blockHash = Hex.toHexString(hash);

					// we check if somebody else managed to download this block in the meanwhile
					if ((blocks[pos] = hashToBlock.get(blockHash)) == null && downloadBlock(blocks, pos, hash, blockHash).isEmpty())
						return false;
				}
			}

			return true;
		}

		private Optional<Block> downloadBlock(Block[] blocks, int pos, byte[] hash, String blockHash) throws InterruptedException, ClosedPeerException, PeerTimeoutException, ClosedDatabaseException {
			Optional<Block> maybeBlock = peers.getBlock(peer, hash);

			if (maybeBlock.isPresent()) {
				Block block = maybeBlock.get();

				// we check that the block has actually the required hash
				if (!Arrays.equals(hash, block.getHash())) {
					LOGGER.warning("sync: block downloading from " + peer + " stops because the peer answered with a block for the wrong hash");
					peers.ban(peer);
					return Optional.empty();
				}

				hashToBlock.put(blockHash, block);
				blocks[pos] = block;
				addToProcess(block, this);
				//if (block.getDescription().getHeight() % 1000 == 0)
					//System.out.println("downloaded " + block.getDescription().getHeight());

				return maybeBlock;
			}
			else {
				// we have no evidence to ban the peer: it might just have been removed or it might have garbage-collected the block
				LOGGER.warning("sync: block downloading from " + peer + " stops because the peer cannot find the block for a hash that it provided");
				return Optional.empty();
			}
		}

		/**
		 * Download the next group of hashes.
		 */
		private Optional<byte[][]> downloadNextGroupOfBlockHashes(Optional<byte[]> lastHashOfPreviousGroup) throws InterruptedException, ClosedPeerException, PeerTimeoutException, ClosedDatabaseException, PortionRejectedException {
			long height = getHeight();
			LOGGER.info("sync: downloading from " + peer + " the hashes of the blocks at height [" + height + ", " + (height + synchronizationGroupSize - 1) + "]");
			Optional<ChainPortion> maybeChain = peers.getChainPortion(peer, height, synchronizationGroupSize);

			if (maybeChain.isPresent()) {
				Optional<byte[]> genesisHash;

				var hashes = maybeChain.get().getHashes().toArray(byte[][]::new);
				if (hashes.length > synchronizationGroupSize + 1) {
					peers.ban(peer); // if a peer sends inconsistent information, we take note
					return Optional.empty();
				}
				// the first hash must coincide with the last hash of the previous group, or otherwise the peer is cheating
				// or there has been a change in the best chain and we must anyway stop downloading blocks from this peer;
				// note that we have no evidence for banning the peer
				else if (hashes.length > 0 && lastHashOfPreviousGroup.isPresent() && !Arrays.equals(hashes[0], lastHashOfPreviousGroup.get()))
					return Optional.empty();
				// if synchronization occurs from the genesis and the genesis of the blockchain is set,
				// then the first hash must be that genesis' hash
				else if (hashes.length > 0 && height == 0L && (genesisHash = blockchain.getGenesisHash()).isPresent() && !Arrays.equals(hashes[0], genesisHash.get())) {
					peers.ban(peer);
					return Optional.empty();
				}

				return Optional.of(hashes);
			}

			return Optional.empty();
		}
	}

	/**
	 * A task of the partial verification stage of the pipeline.
	 */
	private class BlockNonContextualVerifier {
		private final int num;
		private volatile boolean terminated;
	
		private BlockNonContextualVerifier(int num) {
			this.num = num;
		}

		private void run() {
			try {
				while (true) {
					Block block;

					synchronized (blocksToVerify) {
						while (blocksToVerify.isEmpty() && !allDownloadersHaveTerminated())
							blocksToVerify.wait();
	
						if (blocksToVerify.isEmpty()) {
							LOGGER.info("sync: non-contextual block verifier #" + num + " stops since there are no more blocks to verify");
							return;
						}
	
						block = blocksToVerify.removeFirst();
					}

					try {
						if (!blockchain.containsBlock(block.getHash()))
							// we only perform non-contextual verification and only if the block was not already in blockchain,
							// since in that case it must have been verified already; the block might already be in blockchain
							// for the first blocks downloaded during synchronization
							new BlockVerification(null, node, config, block, Optional.empty(), Mode.ABSOLUTE);
	
						synchronized (blocksPartiallyVerified) {
							blocksPartiallyVerified.add(block);
							blocksPartiallyVerified.notifyAll();
						}
					}
					catch (VerificationException e) {
						markAsProcessed(block);
						String blockHash = block.getHexHash();
						LOGGER.warning("sync: non-contextual verification of block " + blockHash + " failed, I'm banning all peers that downloaded that block: " + e.getMessage());
						banDownloadersOf(blockHash);
					}

					//if (block.getDescription().getHeight() % 1000 == 0)
						//System.out.println("  verified " + block.getDescription().getHeight());
				}
			}
			catch (InterruptedException e) {
				Thread.currentThread().interrupt();
				LOGGER.warning("sync: non-contextual block verifier #" + num + " has been interrupted");
			}
			catch (ApplicationTimeoutException | ClosedApplicationException | MisbehavingApplicationException e) {
				LOGGER.warning("sync: non-contextual block verifier #" + num + " stops since the application is misbehaving: " + e.getMessage());
			}
			catch (ClosedDatabaseException e) {
				LOGGER.warning("sync: non-contextual block verifier #" + num + " stops because the database has been closed: " + e.getMessage());
			}
			catch (RuntimeException e) {
				LOGGER.log(Level.SEVERE, "sync: non-contextual block verifier #" + num + " stops since the node is misbehaving", e);
			}
			finally {
				terminated = true;

				// we notify whose was waiting for blocks to add to blockchain, since it might decide to stop as well
				synchronized (blocksPartiallyVerified) {
					blocksPartiallyVerified.notifyAll();
				}
	
				LOGGER.info("sync: stopped non-contextual block verifier #" + num);
			}
		}
	}

	/**
	 * A task of the block adding stage of the pipeline.
	 */
	private class BlockAdder {
		private final int num;

		private BlockAdder(int num) {
			this.num = num;
		}

		private void run() {
			try {
				while (true) {
					Block block;

					synchronized (blocksPartiallyVerified) {
						while (blocksPartiallyVerified.isEmpty() && !allNonContextualVerifiersHaveTerminated())
							blocksPartiallyVerified.wait();

						if (blocksPartiallyVerified.isEmpty()) {
							LOGGER.info("sync: block adder #" + num + " stops since there are no more blocks to add");
							return;
						}

						// we guarantee to process blocks only if all blocks with a smaller height
						// have already been processed (added to blockchain or rejected)
						if (blocksPartiallyVerified.first().getDescription().getHeight() <= minimalHeightStillToProcess())
							block = blocksPartiallyVerified.removeFirst();
						else {
							// otherwise we wait some time and then check again if we can proceed
							blocksPartiallyVerified.wait(100);
							continue;
						}
					}

					String blockHash = block.getHexHash();

					try {
						// we only perform the relative (contextual) checks, since the absolute ones have been performed by the non-contextual verifiers
						if (!blockchain.connect(block, Optional.of(Mode.RELATIVE))) {
							// if the block could not be connected to the blockchain tree, it either means that
							// the downloader peer is forging blocks, or that it is providing a very weak history, on a branch that has
							// been garbage-collected in this node; therefore, banning such a peer looks like the right thing to do
							LOGGER.warning("sync: block " + blockHash + " could not be connected: I'm banning all peers that downloaded that block");
							banDownloadersOf(blockHash);
						}
					}
					catch (VerificationException e) {
						LOGGER.warning("sync: contextual verification of block " + blockHash + " failed, I'm banning all peers that downloaded that block: " + e.getMessage());
						// block verification might fail also if the state of the previous block of "block" has been garbage-collected;
						// but that means that the peer is providing a very weak history, on a branch that has been garbage-collected in this node;
						// therefore, banning such a peer looks like the right thing to do
						banDownloadersOf(blockHash);
					}
					finally {
						markAsProcessed(block);

						//if (block.getDescription().getHeight() % 1000 == 0)
							//System.out.println("     added " + block.getDescription().getHeight());
					}

					// a new block has been processed: we wake up anybody was waiting for an increase of the height that can be processed
					synchronized (blocksPartiallyVerified) {
						blocksPartiallyVerified.notifyAll();
					}
				}
			}
			catch (InterruptedException e) {
				Thread.currentThread().interrupt();
				LOGGER.warning("sync: block adder #" + num + " has been interrupted");
			}
			catch (ApplicationTimeoutException | MisbehavingApplicationException | ClosedApplicationException e) {
				LOGGER.warning("sync: block adder #" + num + " stops because of an application problem: " + e.getMessage());
			}
			catch (ClosedDatabaseException e) {
				LOGGER.warning("sync: block adder #" + num + " stops because the database has been closed: " + e.getMessage());
			}
			catch (RuntimeException e) {
				LOGGER.log(Level.SEVERE, "sync: block adder #" + num + " stops because the node is misbehaving", e);
			}
			finally {
				LOGGER.info("sync: stopped block adder #" + num);
				blockAddersHaveTerminated.release();
			}
		}
	}

	private static class BlockComparatorByHeight implements Comparator<Block> {

		@Override
		public int compare(Block block1, Block block2) {
			int diff = Long.compare(block1.getDescription().getHeight(), block2.getDescription().getHeight());
			if (diff != 0)
				return diff;
			else
				return Arrays.compare(block1.getHash(), block2.getHash());
		}
	}
}