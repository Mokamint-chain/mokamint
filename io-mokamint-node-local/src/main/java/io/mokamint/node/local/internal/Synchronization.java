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
import java.util.Random;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Semaphore;
import java.util.logging.Level;
import java.util.logging.Logger;

import io.hotmoka.annotations.GuardedBy;
import io.hotmoka.crypto.Hex;
import io.mokamint.node.api.Block;
import io.mokamint.node.api.BlockDescription;
import io.mokamint.node.api.ChainPortion;
import io.mokamint.node.api.NodeException;
import io.mokamint.node.api.Peer;
import io.mokamint.node.api.PeerException;
import io.mokamint.node.api.PeerInfo;
import io.mokamint.node.local.ApplicationTimeoutException;
import io.mokamint.node.local.internal.BlockVerification.Mode;

public class Synchronization {
	private final LocalNodeImpl node;
	private final PeersSet peers;
	private final Downloader[] downloaders;

	@GuardedBy("itself")
	private final SortedSet<Block> blocksDownloadedNotYetProcessed = new TreeSet<>(new BlockComparatorByHeight());

	@GuardedBy("itself")
	private final SortedSet<Block> blocksToVerify = new TreeSet<>(new BlockComparatorByHeight());

	@GuardedBy("itself")
	private final SortedSet<Block> blocksNonContextuallyVerified = new TreeSet<>(new BlockComparatorByHeight());

	private final ConcurrentMap<String, Block> hashToBlock = new ConcurrentHashMap<>();
	private final int synchronizationGroupSize;
	private final Blockchain blockchain;
	private final ExecutorService executors;
	private final Semaphore blockAddersHaveTerminated = new Semaphore(0);
	private final long startingHeight;
	private final BlockNonContextualVerifier[] nonContextualVerifiers;
	private final BlockAdder[] blockAdders;

	private final static Logger LOGGER = Logger.getLogger(Synchronization.class.getName());

	public Synchronization(LocalNodeImpl node, ExecutorService executors) throws InterruptedException, NodeException {
		this.node = node;
		this.peers = node.getPeers();
		this.synchronizationGroupSize = node.getConfig().getSynchronizationGroupSize();
		this.blockchain = node.getBlockchain();
		this.executors = executors;
		this.startingHeight = Math.max(blockchain.getStartOfNonFrozenPart().map(Block::getDescription).map(BlockDescription::getHeight).orElse(0L), blockchain.getHeightOfHead().orElse(0L) - 1000L);
		Thread.sleep(20000);
		this.downloaders = mkBlockDownloaders();
		this.nonContextualVerifiers = mkNonContextualVerifiers();
		this.blockAdders = mkBlockAdders();
		startBlockAdders();
		startNonContextualVerifiers();
		startBlockDownloaders();
		waitUntilBlockAddersTerminate();
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
		var verifiers = new BlockNonContextualVerifier[Runtime.getRuntime().availableProcessors() + 1];
		for (int pos = 0; pos < verifiers.length; pos++)
			verifiers[pos] = new BlockNonContextualVerifier(pos);

		return verifiers;
	}

	private BlockAdder[] mkBlockAdders() {
		var adders = new BlockAdder[Runtime.getRuntime().availableProcessors() + 1];
		for (int pos = 0; pos < adders.length; pos++)
			adders[pos] = new BlockAdder(pos);

		return adders;
	}

	// synchronization is needed to avoid assigning the same block to download twice
	private synchronized boolean requestToDownload(String blockHash, Downloader downloader) {
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

	@GuardedBy("this.blocksDownloadedNotYetProcessed")
	private int processingCounter;

	private void markAsProcessed(Block block) {
		boolean cleanUp;

		synchronized (blocksDownloadedNotYetProcessed) {
			blocksDownloadedNotYetProcessed.remove(block);
			cleanUp = ++processingCounter % 1000 == 0;
		}

		for (var downloader: downloaders)
			if (downloader.blocksAddedToProcessByThis.contains(block))
				downloader.queueHasSpace.release();

		if (cleanUp)
			cleanUpDownloaders();
	}

	private long minimalHeightStillToProcess() {
		long result = Long.MAX_VALUE;
		for (var downloader: downloaders)
			if (!downloader.terminated)
				result = Math.min(result, downloader.getHeight());

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

	private void banDownloadersOf(String blockHash) throws NodeException {
		for (var downloader: downloaders)
			if (!downloader.terminated && downloader.blocksRequestedByThis.contains(blockHash))
				downloader.ban();
	}

	private class Downloader {
		private final Peer peer;

		private volatile boolean terminated;

		@GuardedBy("this")
		private long height;

		private final Set<String> blocksRequestedByThis = ConcurrentHashMap.newKeySet();
		private final Set<Block> blocksAddedToProcessByThis = ConcurrentHashMap.newKeySet();
		private final Semaphore queueHasSpace = new Semaphore(synchronizationGroupSize * 2);

		private Downloader(Peer peer) {
			this.peer = peer;
			this.height = startingHeight;
		}

		private void run() {
			try {
				try {
					Optional<byte[]> lastHashOfPreviousGroup = Optional.empty();
		
					while (!terminated) {
						Optional<byte[][]> maybeHashes = downloadNextGroupOfBlockHashes(getHeight(), lastHashOfPreviousGroup);
						byte[][] hashes;
		
						if (maybeHashes.isEmpty() || (hashes = maybeHashes.get()).length == 0)
							break;
		
						if (!downloadNextGroupOfBlocks(hashes))
							return;
		
						if (hashes.length < synchronizationGroupSize + 1) // if the group of hashes was not full, we stop
							break;
		
						lastHashOfPreviousGroup = Optional.of(hashes[hashes.length - 1]);
		
						synchronized (this) {
							height += synchronizationGroupSize;
						}
					}
		
					LOGGER.warning("sync: block downloading from " + peer + " stops because no useful hashes have been provided anymore by the peer");
				}
				catch (PeerTimeoutException | PeerException e) {
					peers.ban(peer);
					LOGGER.warning("sync: block downloading from " + peer + " stops because the peer is misbehaving: " + e.getMessage());
				}
				catch (InterruptedException e) {
					Thread.currentThread().interrupt();
					LOGGER.warning("sync: block downloading from " + peer + " has been interrupted");
				}
				finally {
					terminated = true;
		
					synchronized (blocksToVerify) {
						blocksToVerify.notifyAll();
					}
		
					System.out.println("Stopped downloader for " + peer);
				}
			}
			catch (NodeException | RuntimeException e) {
				LOGGER.log(Level.SEVERE, "sync: block downloading from " + peer + " stops because the node is misbehaving", e);
			}
		}

		private void ban() throws NodeException {
			terminated = true;
			peers.ban(peer);

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

			synchronized (blocksNonContextuallyVerified) {
				blocksNonContextuallyVerified.removeAll(toRemove);
			}
		}

		private synchronized long getHeight() {
			return height;
		}

		private boolean downloadNextGroupOfBlocks(byte[][] hashes) throws InterruptedException, NodeException, PeerException, PeerTimeoutException {
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

		private Optional<Block> downloadBlock(Block[] blocks, int pos, byte[] hash, String blockHash) throws InterruptedException, NodeException, PeerException, PeerTimeoutException {
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

				if ((height + pos) % 1000 == 0)
					System.out.println("Peer " + peer + " Downloaded block at height " + (height + pos));

				return maybeBlock;
			}
			else {
				// we have no evidence to ban the peer: it might just have been removed or it might have garbage-collected the block
				LOGGER.warning("sync: block downloading from " + peer + " stops because the peer cannot find the block for a hash that it provided");
				return Optional.empty();
			}
		}

		/**
		 * Download, into the {@link #groups} map, the next group of hashes with the given peer.
		 * 
		 * @param peer the peer
		 * @throws InterruptedException if the execution has been interrupted
		 * @throws NodeException if the node is misbehaving
		 */
		private Optional<byte[][]> downloadNextGroupOfBlockHashes(long height, Optional<byte[]> lastHashOfPreviousGroup) throws InterruptedException, PeerException, PeerTimeoutException, NodeException {
			LOGGER.info("sync: downloading from " + peer + " the hashes of the blocks at height [" + height + ", " + (height + synchronizationGroupSize) + "]");

			Optional<ChainPortion> maybeChain = peers.getChainPortion(peer, height, synchronizationGroupSize + 1);

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

	// TODO: remove at the end
	private final Set<Block> allVerified = ConcurrentHashMap.newKeySet();

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
							LOGGER.warning("sync: the non-contextual block verifier #" + num + " stops since there are no more blocks to verify");
							return;
						}
	
						block = blocksToVerify.removeFirst();
					}

					if (!allVerified.add(block))
						System.out.println("Repeated verification of block at height " + block.getDescription().getHeight());

					try {
						new BlockVerification(null, node, block, Optional.empty(), Mode.ABSOLUTE);
	
						synchronized (blocksNonContextuallyVerified) {
							blocksNonContextuallyVerified.add(block);
							blocksNonContextuallyVerified.notifyAll();
						}
	
						if (block.getDescription().getHeight() % 4000 == 0)
							System.out.println("sync: the non-contextual block verifier #" + num + " verified block at height " + block.getDescription().getHeight());
					}
					catch (VerificationException e) {
						markAsProcessed(block);
						String blockHash = block.getHexHash();
						LOGGER.warning("sync: the non-contextual verification of block " + blockHash + " failed, I'm banning all peers that provided that block: " + e.getMessage());
						banDownloadersOf(blockHash);
					}
				}
			}
			catch (InterruptedException e) {
				Thread.currentThread().interrupt();
				LOGGER.warning("sync: the non-contextual block verifier #" + num + " has been interrupted");
			}
			catch (ApplicationTimeoutException e) {
				LOGGER.warning("sync: the non-contextual block verifier #" + num + " stops since the application is misbehaving: " + e.getMessage());
			}
			catch (NodeException | RuntimeException e) {
				LOGGER.log(Level.SEVERE, "sync: the non-contextual block verifier #" + num + " stops since the node is misbehaving", e);
			}
			finally {
				terminated = true;

				synchronized (blocksNonContextuallyVerified) {
					blocksNonContextuallyVerified.notifyAll();
				}
	
				LOGGER.info("sync: stopped non-contextual block verifier #" + num);
				System.out.println("sync: non-contextual block verifier #" + num + " stops");
			}
		}
	}

	private class BlockAdder {
		private final int num;

		private BlockAdder(int num) {
			this.num = num;
		}

		private void run() {
			try {
				try {
					while (true) {
						Block block;

						synchronized (blocksNonContextuallyVerified) {
							while (blocksNonContextuallyVerified.isEmpty() && !allNonContextualVerifiersHaveTerminated())
								blocksNonContextuallyVerified.wait();

							if (blocksNonContextuallyVerified.isEmpty()) {
								LOGGER.info("sync: the block adder #" + num + " stops since there are no more blocks to add");
								return;
							}

							// we guarantee that all blocks with a smaller height than the first block in the queue
							// have already been verified; this ensures that the previous block has been already added in
							// blockchain, or has been rejected because it does not verify
							if (blocksNonContextuallyVerified.first().getDescription().getHeight() <= minimalHeightStillToProcess())
								block = blocksNonContextuallyVerified.removeFirst();
							else {
								blocksNonContextuallyVerified.wait(1000);
								continue;
							}
						}

						String blockHash = block.getHexHash();

						try {
							// we only perform the relative checks, since the absolute ones have been performed by the non-contextual verifiers
							if (!blockchain.connect(block, Optional.of(Mode.RELATIVE))) {
								// if the block could not be connected to the blockchain tree, it either means that
								// the downloader peer is forging blocks, or that it is providing a very weak history, on a branch that has
								// been garbage-collected;therefore, banning such a peer looks like the right thing to do
								LOGGER.warning("sync: block " + blockHash + " could not be connected: I'm banning all peers that provided that block");
								banDownloadersOf(blockHash);
							}
						}
						catch (VerificationException e) {
							LOGGER.warning("sync: the contextual verification of block " + blockHash + " failed, I'm banning all peers that provided that block: " + e.getMessage());
							// block verification might fail also if the state of the previous block of "block" has been garbage-collected;
							// but that means that the peer is providing a very weak history, on a branch that has been garbage-collected;
							// therefore, banning such a peer looks like the right thing to do
							banDownloadersOf(blockHash);
						}
						finally {
							markAsProcessed(block);
						}

						if (block.getDescription().getHeight() % 1000 == 0)
							System.out.println("sync: the block adder #" + num + " added block at height " + block.getDescription().getHeight());

						// a new block has been processed: we wake up anybody was waiting
						// for an increase of the height that can be processed
						synchronized (blocksNonContextuallyVerified) {
							blocksNonContextuallyVerified.notifyAll();
						}
					}
				}
				catch (InterruptedException e) {
					Thread.currentThread().interrupt();
					LOGGER.warning("sync: block adder #" + num + " has been interrupted");
				}
				catch (ApplicationTimeoutException e) {
					LOGGER.warning("sync: block adder #" + num + " stops because the application is misbehaving: " + e.getMessage());
				}
				finally {
					LOGGER.info("sync: stopped block adder #" + num);
					System.out.println("sync: block adder #" + num + " stops");
					blockAddersHaveTerminated.release();
				}
			}
			catch (NodeException e) {
				throw new RuntimeException(e);
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