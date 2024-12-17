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

import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeoutException;
import java.util.logging.Level;
import java.util.logging.Logger;

import io.mokamint.node.api.NodeException;
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

	private final Object onBlockAddedWaitingLock = new Object();
	private final Object onMinerAddedWaitingLock = new Object();
	private final Object onSynchronizationCompletedWaitingLock = new Object();

	/**
	 * The code that performs the current mining. It gets swapped each time a new block is being mined.
	 */
	private volatile BlockMiner blockMiner;

	private final static Logger LOGGER = Logger.getLogger(MiningTask.class.getName());

	public MiningTask(LocalNodeImpl node) {
		this.node = node;
	}

	@Override
	public void body() {
		try {
			while (true)
				mineOverHead();
		}
		catch (RejectedExecutionException | ClosedDatabaseException e) {
			LOGGER.warning("mining: exiting since the node is shutting down");
		}
		catch (InterruptedException e) {
			LOGGER.warning("mining: exiting since the node is shutting down");
			Thread.currentThread().interrupt();
		}
		catch (Exception e) {
			LOGGER.log(Level.SEVERE, "mining: dying because of exception", e);
		}
	}

	/**
	 * Called when mining should be interrupted and restarted from the current head of the blockchain.
	 */
	public void restartFromCurrentHead() {
		var blockMiner = this.blockMiner;
		if (blockMiner != null)
			blockMiner.interrupt();
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
	 * @throws NodeException if the node is misbehaving
	 */
	public void add(TransactionEntry entry) throws NodeException {
		var blockMiner = this.blockMiner;
		if (blockMiner != null)
			blockMiner.add(entry);
	}

	private void mineOverHead() throws NodeException, InterruptedException {
		if (node.getBlockchain().isEmpty()) {
			LOGGER.warning("mining: cannot mine on an empty blockchain, will retry later");

			synchronized (onBlockAddedWaitingLock) {
				onBlockAddedWaitingLock.wait(2000L);
			}
		}
		else if (node.getMiners().isEmpty()) {
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
			try {
				// object construction must be separated from its execution, since this allows
				// to have a reference trough which the block miner to be interrupted if the current head changes
				blockMiner = new BlockMiner(node);
				blockMiner.mine();
			}
			catch (TimeoutException e) {
				LOGGER.log(Level.SEVERE, "mining: the application is not answering: I will wait five seconds and then try again", e);
				Thread.sleep(5000L);
			}
		}
	}
}