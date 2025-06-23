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

import java.util.logging.Level;
import java.util.logging.Logger;

import io.mokamint.node.api.NodeException;
import io.mokamint.node.local.ApplicationTimeoutException;
import io.mokamint.node.local.internal.LocalNodeImpl.Task;
import io.mokamint.node.local.internal.Mempool.TransactionEntry;

/**
 * A task that mines blocks. It executes an internal thread that
 * repeatedly takes the head of the blockchain and builds a new block on top of it.
 * This task might terminate only because of an interruption (or exception).
 */
public class MiningTask implements Task {

	/**
	 * The node for which the block is being mined.
	 */
	private final LocalNodeImpl node;

	/**
	 * A lock used to when mining is suspended, to wait for an event that might make it able to run again.
	 */
	private final Object waitingLock = new Object();

	/**
	 * The task that is performing the current mining. It gets swapped each time a new block starts being mined.
	 */
	private volatile BlockMiner blockMiner;

	private final static Logger LOGGER = Logger.getLogger(MiningTask.class.getName());

	public MiningTask(LocalNodeImpl node) {
		this.node = node;
	}

	@Override
	public void body() throws NodeException, InterruptedException {
		try {
			while (true)
				mineOverHead();
		}
		catch (TaskRejectedExecutionException e) {
			LOGGER.warning("mining: exiting since the node is shutting down");
		}
	}

	/**
	 * Called when mining should be interrupted and restarted from the current head of the blockchain.
	 */
	public void restartFromCurrentHead() {
		if (!node.isSynchronizing()) {
			var blockMiner = this.blockMiner;
			if (blockMiner != null)
				blockMiner.interrupt();
		}
	}

	/**
	 * Continue mining, if suspended because some precondition was missing. For instance,
	 * mining gets suspended if the blockchain is empty or there are no miners or the node is synchronizing.
	 */
	public void continueIfSuspended() {
		synchronized (waitingLock) {
			waitingLock.notify();
		}
	}

	/**
	 * Adds the given transaction to the mempool used by this task. This allows the task
	 * to process transactions that arrive during the mining of a next block.
	 * 
	 * @param entry the transaction entry
	 * @throws NodeException if the node is misbehaving
	 */
	public void add(TransactionEntry entry) throws NodeException {
		var blockMiner = this.blockMiner;
		if (blockMiner != null)
			blockMiner.add(entry);
	}

	private void mineOverHead() throws NodeException, InterruptedException, TaskRejectedExecutionException {
		if (node.getBlockchain().isEmpty()) {
			LOGGER.warning("mining: cannot mine on an empty blockchain, will retry later");
			suspendUntilSomethingChanges();
		}
		else if (node.getMiners().isEmpty()) {
			LOGGER.warning("mining: cannot mine with no miners attached, will retry later");
			node.onNoMinersAvailable();
			suspendUntilSomethingChanges();
		}
		else if (node.isSynchronizing()) {
			LOGGER.warning("mining: cannot mine since synchronization is in progress, will retry later");
			suspendUntilSomethingChanges();
		}
		else {
			try {
				// object construction must be separated from its execution, since this allows
				// to have a reference trough which the block miner can be interrupted if the current head changes
				blockMiner = new BlockMiner(node);
				blockMiner.mine();
			}
			catch (ApplicationTimeoutException e) {
				LOGGER.warning("mining: the application is unresponsive: I will wait five seconds and then try again: " + e.getMessage());
				Thread.sleep(5000L);
			}
			catch (NodeException e) {
				LOGGER.log(Level.SEVERE, "restarting the mining task after crash", e);
			}
		}
	}

	private void suspendUntilSomethingChanges() throws InterruptedException {
		synchronized (waitingLock) {
			waitingLock.wait(10_000L);
		}
	}
}