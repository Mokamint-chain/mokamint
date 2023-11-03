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

package io.mokamint.node.api;

import java.security.NoSuchAlgorithmException;
import java.util.Optional;
import java.util.concurrent.TimeoutException;
import java.util.stream.Stream;

import io.hotmoka.annotations.ThreadSafe;

/**
 * The public interface of a node of a Mokamint blockchain.
 * Typically, this API can be called from every machine.
 */
@ThreadSafe
public interface PublicNode extends Node, Whisperer {

	/**
	 * Yields non-consensus information about the node.
	 * 
	 * @return the information
	 * @throws TimeoutException if no answer arrives before a time window
	 * @throws InterruptedException if the current thread is interrupted while waiting for an answer to arrive
	 * @throws ClosedNodeException if the node is closed
	 */
	NodeInfo getInfo() throws TimeoutException, InterruptedException, ClosedNodeException;

	/**
	 * Yields the consensus configuration parameters of this node.
	 * 
	 * @return the consensus parameters
	 * @throws TimeoutException if no answer arrives before a time window
	 * @throws InterruptedException if the current thread is interrupted while waiting for an answer to arrive
	 * @throws ClosedNodeException if the node is closed
	 */
	ConsensusConfig<?,?> getConfig() throws TimeoutException, InterruptedException, ClosedNodeException;

	/**
	 * Yields information about the peers this node is connected to. There is a dynamic
	 * set of peers connected to a node, potentially zero or more peers.
	 * Peers might be connected or disconnected to the node at any moment.
	 * 
	 * @return the peers information
	 * @throws TimeoutException if no answer arrives before a time window
	 * @throws InterruptedException if the current thread is interrupted while waiting for an answer to arrive
	 * @throws ClosedNodeException if the node is closed
	 */
	Stream<PeerInfo> getPeerInfos() throws TimeoutException, InterruptedException, ClosedNodeException;

	/**
	 * Yields information about the miners this node uses. A node uses a dynamic,
	 * potentially empty set of miners. Miners might be added or removed from a node at any moment.
	 * 
	 * @return the miners information
	 * @throws TimeoutException if no answer arrives before a time window
	 * @throws InterruptedException if the current thread is interrupted while waiting for an answer to arrive
	 * @throws ClosedNodeException if the node is closed
	 */
	Stream<MinerInfo> getMinerInfos() throws TimeoutException, InterruptedException, ClosedNodeException;

	/**
	 * Yields information about the tasks currently running inside this node.
	 * 
	 * @return the tasks information
	 * @throws TimeoutException if no answer arrives before a time window
	 * @throws InterruptedException if the current thread is interrupted while waiting for an answer to arrive
	 * @throws ClosedNodeException if the node is closed
	 */
	Stream<TaskInfo> getTaskInfos() throws TimeoutException, InterruptedException, ClosedNodeException;

	/**
	 * Yields information about the current chain of this node.
	 * 
	 * @return the information
	 * @throws DatabaseException if the database is corrupted
	 * @throws TimeoutException if no answer arrives before a time window
	 * @throws InterruptedException if the current thread is interrupted while waiting for an answer to arrive
	 * @throws ClosedNodeException if the node is closed
	 */
	ChainInfo getChainInfo() throws DatabaseException, TimeoutException, InterruptedException, ClosedNodeException;

	/**
	 * Yields a portion of the current best chain, containing the hashes of the blocks starting at height {@code start}
	 * (inclusive) and ending at height {@code start + count} (exclusive). The result
	 * might actually be shorter if the current best chain is shorter than {@code start + count} blocks.
	 * 
	 * @param start the height of the first block whose hash is returned
	 * @param count how many hashes (at most) must be reported
	 * @return the portion with the hashes, in order
	 * @throws DatabaseException if the database is corrupted
	 * @throws TimeoutException if no answer arrives before a time window
	 * @throws InterruptedException if the current thread is interrupted while waiting for an answer to arrive
	 * @throws ClosedNodeException if the node is closed
	 */
	ChainPortion getChainPortion(long start, int count) throws DatabaseException, TimeoutException, InterruptedException, ClosedNodeException;

	/**
	 * Yields the block with the given hash, if it has been seen by this node.
	 * 
	 * @param hash the hash of the block
	 * @return the block, if any
	 * @throws DatabaseException if the database is corrupted
	 * @throws NoSuchAlgorithmException if the block exists but uses an unknown hashing or signature algorithm
	 * @throws TimeoutException if no answer arrives before a time window
	 * @throws InterruptedException if the current thread is interrupted while waiting for an answer to arrive
	 * @throws ClosedNodeException if the node is closed
	 */
	Optional<Block> getBlock(byte[] hash) throws DatabaseException, NoSuchAlgorithmException, TimeoutException, InterruptedException, ClosedNodeException;

	/**
	 * Yields the description of the block with the given hash, if it has been seen by this node.
	 * 
	 * @param hash the hash of the block
	 * @return the description of the block, if any
	 * @throws DatabaseException if the database is corrupted
	 * @throws NoSuchAlgorithmException if the block exists but uses an unknown hashing or signature algorithm
	 * @throws TimeoutException if no answer arrives before a time window
	 * @throws InterruptedException if the current thread is interrupted while waiting for an answer to arrive
	 * @throws ClosedNodeException if the node is closed
	 */
	Optional<BlockDescription> getBlockDescription(byte[] hash) throws DatabaseException, NoSuchAlgorithmException, TimeoutException, InterruptedException, ClosedNodeException;

	/**
	 * Checks the validity of the given transaction and adds it to the mempool of this node.
	 * The node will afterwards whisper the transaction to all its peers.
	 * 
	 * @param transaction the transaction
	 * @return information about the transaction that has been added to the mempool
	 * @throws TimeoutException if no answer arrives before a time window
	 * @throws InterruptedException if the current thread is interrupted while waiting for an answer to arrive
	 * @throws ClosedNodeException if the node is closed
	 */
	TransactionInfo add(Transaction transaction) throws TimeoutException, InterruptedException, ClosedNodeException;

	/**
	 * Yields information about the transaction with the given hash, if it has been already
	 * inserted inside a block of the current chain.
	 * 
	 * @param hash the hash of the transaction
	 * @return the transaction, if any
	 * @throws DatabaseException if the database is corrupted
	 * @throws NoSuchAlgorithmException if the transaction exists but is inside a block that uses an unknown hashing or signature algorithm
	 * @throws TimeoutException if no answer arrives before a time window
	 * @throws InterruptedException if the current thread is interrupted while waiting for an answer to arrive
	 * @throws ClosedNodeException if the node is closed
	 */
	//Optional<Transaction> getTransaction(byte[] hash) throws DatabaseException, NoSuchAlgorithmException, TimeoutException, InterruptedException, ClosedNodeException;

	/**
	 * Yields information about the transaction with the given hash, if it is currently inside the mempool of the node.
	 * 
	 * @param hash the hash of the transaction
	 * @return the transaction, if any
	 * @throws TimeoutException if no answer arrives before a time window
	 * @throws InterruptedException if the current thread is interrupted while waiting for an answer to arrive
	 * @throws ClosedNodeException if the node is closed
	 */
	//Optional<Transaction> getMempoolTransaction(byte[] hash) throws TimeoutException, InterruptedException, ClosedNodeException;

	/**
	 * Yields information about the mempool of this node.
	 * 
	 * @return the information
	 * @throws TimeoutException if no answer arrives before a time window
	 * @throws InterruptedException if the current thread is interrupted while waiting for an answer to arrive
	 * @throws ClosedNodeException if the node is closed
	 */
	//MempoolInfo getMempoolInfo() throws TimeoutException, InterruptedException, ClosedNodeException;

	/**
	 * Yields the portion of the node's mempool containing the entries starting at number {@code start}
	 * (inclusive) and ending at number {@code start + count} (exclusive). The result
	 * might actually be shorter if the current mempool is shorter than {@code start + count} blocks.
	 * 
	 * @param start the number of the first entry that is returned
	 * @param count how many entries (at most) must be reported
	 * @return the portion with the entries, in order
	 * @throws TimeoutException if no answer arrives before a time window
	 * @throws InterruptedException if the current thread is interrupted while waiting for an answer to arrive
	 * @throws ClosedNodeException if the node is closed
	 */
	//MempoolPortion getMempoolPortion(int start, int count) throws TimeoutException, InterruptedException, ClosedNodeException;

	/**
	 * Binds a whisperer to this node. This means that whenever this node
	 * has something to whisper, it will whisper to {@code whisperer} as well.
	 * Note that this method does not state the converse.
	 * 
	 * @param whisperer the whisperer to bind
	 */
	void bindWhisperer(Whisperer whisperer);

	/**
	 * Unbinds a whisperer to this node. This means that this node will stop
	 * whispering to {@code whisperer}.
	 * 
	 * @param whisperer the whisperer to unbind
	 */
	void unbindWhisperer(Whisperer whisperer);
}