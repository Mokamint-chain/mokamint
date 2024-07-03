/*
Copyright 2024 Fausto Spoto

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

import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.SignatureException;
import java.util.Optional;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeoutException;
import java.util.logging.Level;
import java.util.logging.Logger;

import io.mokamint.application.api.ApplicationException;
import io.mokamint.application.api.UnknownGroupIdException;
import io.mokamint.application.api.UnknownStateException;
import io.mokamint.node.DatabaseException;
import io.mokamint.node.api.Block;
import io.mokamint.node.local.internal.LocalNodeImpl.Task;
import io.mokamint.node.local.internal.Mempool.TransactionEntry;

/**
 * A task that mines blocks. It executes an internal thread that
 * takes the head of the blockchain and builds a new block on top of it.
 * This task only terminates by interruption (or exception).
 */
public class MiningTask implements Task {

	/**
	 * The node for which blocks are being mined.
	 */
	private final LocalNodeImpl node;

	/**
	 * The thread that performs the mining.
	 */
	private final MiningThread miningThread = new MiningThread();

	private final Object onBlockAddedWaitingLock = new Object();
	private final Object onMinerAddedWaitingLock = new Object();
	private final Object onSynchronizationCompletedWaitingLock = new Object();

	/**
	 * The code that performs the current mining. It gets swapped each time a new block is being mined.
	 */
	private volatile BlockMiner blockMiner;

	private volatile boolean taskHasBeenInterrupted;

	private final static Logger LOGGER = Logger.getLogger(MiningTask.class.getName());

	public MiningTask(LocalNodeImpl node) {
		this.node = node;
	}

	@Override
	public void body() {
		miningThread.start();

		try {
			miningThread.join();
		}
		catch (InterruptedException e) {
			taskHasBeenInterrupted = true; // so that the mining thread exits when we interrupt it at the following line
			miningThread.interrupt();
			Thread.currentThread().interrupt();
		}
	}

	/**
	 * Called when mining should be interrupted and restarted from the current head of the blockchain.
	 */
	public void restartFromCurrentHead() {
		// since taskHasBeenInterrupted remains false, this will interrupt the current mining activity and restart it on top of the new head
		miningThread.interrupt();
	}

	/**
	 * Called when a new block gets added to the blockchain.
	 */
	public void onBlockAdded() {
		synchronized (onBlockAddedWaitingLock) {
			onBlockAddedWaitingLock.notify();
		}
	}

	/**
	 * Called when a miner gets added to the node.
	 */
	public void onMinerAdded() {
		synchronized (onMinerAddedWaitingLock) {
			onMinerAddedWaitingLock.notify();
		}
	}

	/**
	 * Called when synchronization completes.
	 */
	public void onSynchronizationCompleted() {
		synchronized (onSynchronizationCompletedWaitingLock) {
			onSynchronizationCompletedWaitingLock.notify();
		}
	}

	/**
	 * Adds the given transaction to the mempool used by this task. This allows the task
	 * to process transactions that arrive during the mining of the block.
	 * 
	 * @param entry the transaction entry
	 * @throws {@link NoSuchAlgorithmException} if the blockchain contains a block that refers to an unknown cryptographic algorithm
	 * @throws {@link ClosedDatabaseException} if the database is already closed
	 * @throws {@link DatabaseException} if the database is corrupted
	 */
	public void add(TransactionEntry entry) throws NoSuchAlgorithmException, ClosedDatabaseException, DatabaseException {
		var blockMiner = this.blockMiner;
		if (blockMiner != null)
			blockMiner.add(entry);
	}

	/**
	 * The actual thread that performs the mining.
	 */
	private class MiningThread extends Thread {

		@Override
		public void run() {
			while (!taskHasBeenInterrupted) {
				try {
					Optional<Block> maybeHead = node.getBlockchain().getHead();

					if (maybeHead.isEmpty()) {
						LOGGER.warning("mining: cannot mine on an empty blockchain, will retry later");

						synchronized (onBlockAddedWaitingLock) {
							onBlockAddedWaitingLock.wait(2000L);
						}
					}
					else if (node.getMiners().get().count() == 0L) {
						LOGGER.warning("mining: cannot mine with no miners attached, will retry later");
						node.onNoMinersAvailable();

						synchronized (onMinerAddedWaitingLock) {
							onMinerAddedWaitingLock.wait(2000L);
						}
					}
					else if (node.isSynchronizing()) {
						LOGGER.warning("mining: delaying mining since synchronization is in progress, will retry later");

						synchronized (onSynchronizationCompletedWaitingLock) {
							onSynchronizationCompletedWaitingLock.wait(2000L);
						}
					}
					else {
						var head = maybeHead.get();
						LOGGER.info("mining: starting mining over block " + head.getHexHash(node.getConfig().getHashingForBlocks()));
						blockMiner = new BlockMiner(node, head);
						blockMiner.mine();
					}
				}
				catch (InterruptedException e) {
					LOGGER.info("mining: restarting mining since the blockchain's head changed");
				}
				catch (ClosedDatabaseException e) {
					LOGGER.warning("mining: exiting since the database has been closed");
					break;
				}
				catch (RejectedExecutionException e) {
					LOGGER.warning("mining: exiting since the node is being shut down");
					break;
				}
				catch (ApplicationException | TimeoutException e) {
					LOGGER.log(Level.SEVERE, "mining: the application is misbehaving: I will wait five seconds and then try again", e);

					try {
						Thread.sleep(5000L);
					}
					catch (InterruptedException e2) {
						LOGGER.info("mining: restarting mining");
					}
				}
				catch (UnknownStateException e) {
					LOGGER.log(Level.SEVERE, "mining: exiting since the state of the head of the blockchain is unknown to the application", e);
					break;
				}
				catch (NoSuchAlgorithmException | DatabaseException | InvalidKeyException | SignatureException | UnknownGroupIdException e) {
					LOGGER.log(Level.SEVERE, "mining: exiting because of exception", e);
					break;
				}
				catch (RuntimeException e) {
					LOGGER.log(Level.SEVERE, "mining: exiting because of unexpected exception", e);
					break;
				}
			}
		}
	}
}