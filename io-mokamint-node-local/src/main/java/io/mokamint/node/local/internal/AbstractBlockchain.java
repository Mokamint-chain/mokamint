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

/**
 * 
 */
package io.mokamint.node.local.internal;

import java.security.NoSuchAlgorithmException;
import java.util.stream.Stream;

import io.hotmoka.annotations.ThreadSafe;
import io.mokamint.miner.api.Miner;
import io.mokamint.node.api.Block;
import io.mokamint.node.api.DatabaseException;
import io.mokamint.node.local.internal.LocalNodeImpl.OnAddedTransactionHandler;
import io.mokamint.node.local.internal.mempool.Mempool.TransactionEntry;
import io.mokamint.nonce.api.Deadline;
import io.mokamint.nonce.api.IllegalDeadlineException;

/**
 * Bridge class to give access to protected methods to its subclass,
 * that represents the blockchain of a Mokamint node.
 */
@ThreadSafe
public abstract class AbstractBlockchain {

	/**
	 * The node having this blockchain.
	 */
	private final LocalNodeImpl node;

	/**
	 * Creates the blockchain of a Mokamint node.
	 * 
	 * @param node the node having the blockchain
	 */
	protected AbstractBlockchain(LocalNodeImpl node) {
		this.node = node;
	}

	/**
	 * Yields the node having this blockchain.
	 * 
	 * @return the node having this blockchain
	 */
	protected final LocalNodeImpl getNode() {
		return node;
	}

	/**
	 * @see LocalNodeImpl#check(Deadline).
	 */
	protected void check(Deadline deadline) throws IllegalDeadlineException {
		node.check(deadline);
	}

	/**
	 * @see LocalNodeImpl#isMiningOver(Block).
	 */
	protected boolean isMiningOver(Block previous) {
		return node.isMiningOver(previous);
	}

	/**
	 * @see LocalNodeImpl#onMiningStarted(Block, OnAddedTransactionHandler).
	 */
	protected void onMiningStarted(Block previous, OnAddedTransactionHandler handler) {
		node.onMiningStarted(previous, handler);
	}

	/**
	 * @see LocalNodeImpl#onMiningCompleted(Block, OnAddedTransactionHandler).
	 */
	protected void onMiningCompleted(Block previous, OnAddedTransactionHandler handler) {
		node.onMiningCompleted(previous, handler);
	}

	/**
	 * @see LocalNodeImpl#onBlockAdded(Block).
	 */
	protected void onBlockAdded(Block block) {
		node.onBlockAdded(block);
	}

	/**
	 * @see {@link LocalNodeImpl#onHeadChanged(byte[])}.
	 */
	protected void onHeadChanged(byte[] newHeadHash) {
		node.onHeadChanged(newHeadHash);
	}

	/**
	 * @see {@link LocalNodeImpl#rebaseMempoolAt(byte[])}.
	 */
	protected void rebaseMempoolAt(byte[] newHeadHash) throws NoSuchAlgorithmException, DatabaseException, ClosedDatabaseException {
		node.rebaseMempoolAt(newHeadHash);
	}

	/**
	 * @see {@link LocalNodeImpl#getMempoolTransactionsAt(byte[])}.
	 */
	protected Stream<TransactionEntry> getMempoolTransactionsAt(byte[] newHeadHash) throws NoSuchAlgorithmException, DatabaseException, ClosedDatabaseException {
		return node.getMempoolTransactionsAt(newHeadHash);
	}

	/**
	 * @see LocalNodeImpl#onBlockMined(Block).
	 */
	protected void onBlockMined(Block block) {
		node.onBlockMined(block);
	}

	/**
	 * @see LocalNodeImpl#onNoDeadlineFound(Block).
	 */
	protected void onNoDeadlineFound(Block previous) {
		node.onNoDeadlineFound(previous);
	}

	/**
	 * @see LocalNodeImpl#onIllegalDeadlineComputed(Deadline, Miner).
	 */
	protected void onIllegalDeadlineComputed(Deadline deadline, Miner miner) {
		node.onIllegalDeadlineComputed(deadline, miner);
	}

	/**
	 * @see LocalNodeImpl#onNoMinersAvailable().
	 */
	protected void onNoMinersAvailable() {
		node.onNoMinersAvailable();
	}

	/**
	 * @see LocalNodeImpl#onSynchronizationCompleted().
	 */
	protected void onSynchronizationCompleted() {
		node.onSynchronizationCompleted();
	}

	/**
	 * @see LocalNodeImpl#scheduleWhisperingWithoutAddition(Block).
	 */
	protected void scheduleWhisperingWithoutAddition(Block block) {
		node.scheduleWhisperingWithoutAddition(block);
	}

	/**
	 * @see LocalNodeImpl#scheduleSynchronization(long).
	 */
	protected void scheduleSynchronization(long initialHeight) {
		node.scheduleSynchronization(initialHeight);
	}

	/**
	 * @see LocalNodeImpl#scheduleMining().
	 */
	protected void scheduleMining() {
		node.scheduleMining();
	}

	/**
	 * @see LocalNodeImpl#scheduleDelayedMining().
	 */
	protected void scheduleDelayedMining() {
		node.scheduleDelayedMining();
	}
}