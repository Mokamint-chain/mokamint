/*
Copyright 2021 Fausto Spoto

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

package io.mokamint.node.local.api;

import java.net.URI;
import java.nio.file.Path;

import io.mokamint.node.api.ConsensusConfigBuilder;
import io.mokamint.node.api.Peer;
import io.mokamint.node.api.RestrictedNode;

/**
 * The builder of a configuration of a local Mokamint node.
 */
public interface LocalNodeConfigBuilder extends ConsensusConfigBuilder<LocalNodeConfig, LocalNodeConfigBuilder> {

	/**
	 * Sets the directory where the node's data will be persisted.
	 * It defaults to {@code mokamint-chain} in the current directory.
	 * 
	 * @param dir the directory
	 * @return this builder
	 */
	LocalNodeConfigBuilder setDir(Path dir);

	/**
	 * Sets the maximal delay, in milliseconds, between the deadline request to the miners
	 * and the reception of the first deadline from the miners. This defaults to 20000.
	 * 
	 * @param deadlineWaitTimeout the wait time, in milliseconds
	 * @return this builder
	 */
	LocalNodeConfigBuilder setDeadlineWaitTimeout(long deadlineWaitTimeout);

	/**
	 * Sets the initial points of a miner freshly connected to the node.
	 * These points might be reduced for punishment. If they reach zero, the miner
	 * gets disconnected. This defaults to 1000.
	 * 
	 * @param minerInitialPoints the initial points
	 * @return this builder
	 */
	LocalNodeConfigBuilder setMinerInitialPoints(long minerInitialPoints);

	/**
	 * Sets the points lost by a miner, as punishment for timeout
	 * at a request for a new deadline. This defaults to 1.
	 * 
	 * @param minerPunishmentForTimeout the points
	 * @return this builder
	 */
	LocalNodeConfigBuilder setMinerPunishmentForTimeout(long minerPunishmentForTimeout);

	/**
	 * Sets the points lost by a miner, as punishment for providing an illegal deadline.
	 * This defaults to 500.
	 * 
	 * @param minerPunishmentForIllegalDeadline the points
	 * @return this builder
	 */
	LocalNodeConfigBuilder setMinerPunishmentForIllegalDeadline(long minerPunishmentForIllegalDeadline);

	/**
	 * Adds the given URI to those of the initial peers (called seeds).
	 * Such peers are contacted at start-up.
	 * 
	 * @param uri the URI to add
	 * @return this builder
	 */
	LocalNodeConfigBuilder addSeed(URI uri);

	/**
	 * Sets the maximum number of peers kept by a node. The actual number of peers can
	 * be larger only if peers are explicitly added as seeds or through the
	 * {@link RestrictedNode#add(Peer)} method.
	 * It defaults to 20.
	 * 
	 * @param maxPeers the maximum number of peers
	 * @return this builder
	 */
	LocalNodeConfigBuilder setMaxPeers(long maxPeers);

	/**
	 * Sets the initial points of a peer, freshly added to a node.
	 * These points might be reduced for punishment. If they reach zero, the peer
	 * gets disconnected. This defaults to 1000.
	 * 
	 * @param peerInitialPoints the initial points
	 * @return this builder
	 */
	LocalNodeConfigBuilder setPeerInitialPoints(long peerInitialPoints);

	/**
	 * Sets the maximal difference (in milliseconds) between the local time of a node
	 * and of one of its peers. This defaults to 15,000 (15 seconds).
	 * 
	 * @param peerMaxTimeDifference the maximal time difference (in milliseconds)
	 * @return this builder
	 */
	LocalNodeConfigBuilder setPeerMaxTimeDifference(long peerMaxTimeDifference);

	/**
	 * Sets the points lost by a peer, as punishment for not answering a ping.
	 * This defaults to 1.
	 * 
	 * @param peerPunishmentForUnreachable the points
	 * @return this builder
	 */
	LocalNodeConfigBuilder setPeerPunishmentForUnreachable(long peerPunishmentForUnreachable);

	/**
	 * Sets the time, in milliseconds, allowed to contact a peer. Beyond this threshold, the request timeouts.
	 * 
	 * @param peerTimeout the timeout
	 * @return this builder
	 */
	LocalNodeConfigBuilder setPeerTimeout(long peerTimeout);

	/**
	 * Sets the time interval, in milliseconds, between successive pings to a peer.
	 * Every time the peer does not answer, its points are reduced by {@link LocalNodeConfig#getPeerPunishmentForUnreachable()},
	 * until they reach zero and the peer is removed.  During a successful ping, its peers are collected
	 * if they are useful for the node (for instance, if the node has too few peers).
	 * 
	 * @param peerPingInterval the time interval; use a negative value to disable pinging
	 * @return this builder
	 */
	LocalNodeConfigBuilder setPeerPingInterval(long peerPingInterval);

	/**
	 * Sets the time interval, in milliseconds, between successive broadcasts of a service open on a node.
	 * 
	 * @param serviceBroadcastInterval the time interval; use a negative value to disable broadcasting
	 * @return this builder
	 */
	LocalNodeConfigBuilder setServiceBroadcastInterval(long serviceBroadcastInterval);

	/**
	 * Sets the size of the memory used to avoid whispering the same
	 * message again; higher numbers reduce the circulation of spurious messages.
	 * 
	 * @param whisperingMemorySize the size
	 * @return this builder
	 */
	LocalNodeConfigBuilder setWhisperingMemorySize(int whisperingMemorySize);

	/**
	 * Sets the size of the memory used to hold orphan nodes, that is, nodes received
	 * from the network but having no parent in the blockchain. Larger sizes allow for
	 * out of order reception of blocks, without synchronization.
	 * 
	 * @param orphansMemorySize the size
	 * @return this builder
	 */
	LocalNodeConfigBuilder setOrphansMemorySize(int orphansMemorySize);

	/**
	 * Sets the maximal size of the mempool of the node, that is, of the area
	 * of memory where incoming transactions are held before being verified and added to blocks
	 * 
	 * @param mempoolSize the size
	 * @return this builder
	 */
	LocalNodeConfigBuilder setMempoolSize(int mempoolSize);

	/**
	 * Sets the maximal time (in milliseconds) a block can be created in the future,
	 * from now (intended as network time now). Block verification will reject blocks created
	 * beyond this threshold. It defaults to 15,000 (15 seconds).
	 * 
	 * @param blockMaxTimeInTheFuture the maximal time difference (in milliseconds)
	 * @return this builder
	 */
	LocalNodeConfigBuilder setBlockMaxTimeInTheFuture(long blockMaxTimeInTheFuture);
}