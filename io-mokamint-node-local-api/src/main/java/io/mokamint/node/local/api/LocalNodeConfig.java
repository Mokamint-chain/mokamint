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

package io.mokamint.node.local.api;

import java.net.URI;
import java.nio.file.Path;
import java.util.stream.Stream;

import io.hotmoka.annotations.Immutable;
import io.mokamint.node.api.ConsensusConfig;
import io.mokamint.node.api.Peer;
import io.mokamint.node.api.RestrictedNode;

/**
 * The configuration of a local Mokamint node.
 */
@Immutable
public interface LocalNodeConfig extends ConsensusConfig<LocalNodeConfig, LocalNodeConfigBuilder> {

	/**
	 * Yields the path where the node's data will be persisted.
	 * 
	 * @return the path
	 */
	Path getDir();

	/**
	 * Yields the maximal delay, in milliseconds, between a deadline request to the miners
	 * and the reception of the first deadline from the miners. After this threshold,
	 * deadlines might well arrive, but might get ignored by the node.
	 * 
	 * @return the maximal delay
	 */
	int getDeadlineWaitTimeout();

	/**
	 * Yields the initial points of a miner, freshly connected to a node.
	 * 
	 * @return the initial points of a miner
	 */
	long getMinerInitialPoints();

	/**
	 * Yields the points lost for punishment by a miner that timeouts at a request for a deadline.
	 * 
	 * @return the points lost for punishment by a miner that timeouts
	 */
	long getMinerPunishmentForTimeout();

	/**
	 * Yields the points lost by a miner that provides an illegal deadline.
	 * 
	 * @return the points lost by a miner that provides an illegal deadline
	 */
	long getMinerPunishmentForIllegalDeadline();

	/**
	 * Yields the URIs of the initial peers that must be contacted at start-up
	 * and potentially end-up in the set of active peers.
	 * 
	 * @return the set of URIs of initial peers
	 */
	Stream<URI> getSeeds();

	/**
	 * Yields the maximum number of peers kept by a node. The actual number of peers can
	 * be larger only if peers are explicitly added as seeds or through the
	 * {@link RestrictedNode#add(Peer)} method.
	 * 
	 * @return the maximum number of peers kept by a node
	 */
	int getMaxPeers();

	/**
	 * Yields the initial points of a peer, freshly added to a node.
	 * 
	 * @return the initial points of a peer
	 */
	long getPeerInitialPoints();

	/**
	 * Yields the maximal difference (in milliseconds) between the local time of a node and of one of its peers.
	 * 
	 * @return the maximal difference (in milliseconds) between the local time of a node
	 *         and of one of its peers
	 */
	int getPeerMaxTimeDifference();

	/**
	 * Yields the points lost for punishment by a peer that does not answer to a ping request.
	 * 
	 * @return the points lost for punishment by a peer that does not answer to a ping request
	 */
	long getPeerPunishmentForUnreachable();

	/**
	 * Yields the time, in milliseconds, allowed to contact a peer. Beyond this threshold, the request timeouts.
	 * 
	 * @return the time, in milliseconds, allowed to contact a peer
	 */
	int getPeerTimeout();

	/**
	 * Yields the time interval, in milliseconds, between successive pings to a peer.
	 * Every time the peer does not answer, its points are reduced by {@link #getPeerPunishmentForUnreachable()},
	 * until they reach zero and the peer is removed. During a successful ping, its peers are collected
	 * if they are useful for the node (for instance, if the node has too few peers).
	 * 
	 * @return the time interval, in milliseconds, between successive pings to a peer;
	 *         a negative value means that pinging is disabled
	 */
	int getPeerPingInterval();

	/**
	 * Yields the time interval, in milliseconds, between successive broadcasts of a service open on a node.
	 * 
	 * @return the time interval, in milliseconds, between successive broadcasts;
	 *         a negative value means that broadcast is disabled
	 */
	int getServiceBrodcastInterval();

	/**
	 * Yields the time between successive synchronizations. Note that synchronizations
	 * might be scheduled more frequently if, for instance, a peer gets added or reconnects.
	 * 
	 * @return the time between success synchronizations;
	 *         a negative value means that successive synchronizations are disabled
	 *         and synchronizations only occurs for other reasons, for instance, if a peer
	 *         gets added or reconnects
	 */
	int getSynchronizationInterval();

	/**
	 * Yields the size of the memory used to avoid whispering the same
	 * message again; higher numbers reduce the circulation of spurious messages.
	 * 
	 * @return the size of the memory used to avoid whispering the same message again
	 */
	int getWhisperingMemorySize();

	/**
	 * Yields the size of the memory used to hold orphan nodes, that is, nodes received
	 * from the network but having no parent in the blockchain. Larger sizes allow for
	 * out of order reception of blocks, without synchronization.
	 * 
	 * @return the size of the memory used to hold orphan nodes
	 */
	int getOrphansMemorySize();

	/**
	 * Yields the maximal size of the mempool of the node, that is, of the area
	 * of memory where incoming transactions are held before being verified and added to blocks.
	 * 
	 * @return the size of the mempool of the node
	 */
	int getMempoolSize();

	/**
	 * Yields the size of the group of blocks whose hashes get downloaded
	 * in one shot during synchronization.
	 * 
	 * @return the size of the group
	 */
	int getSynchronizationGroupSize();

	/**
	 * Yields the maximal time (in milliseconds) a block can be created in the future,
	 * from now (intended as network time now). Block verification will reject blocks created
	 * beyond this threshold.
	 * 
	 * @return the maximal time (in milliseconds) a block can be created in the future, from now
	 */
	long getBlockMaxTimeInTheFuture();

	/**
	 * Yields the maximal history change time for the blockchain. That is, part of the history older
	 * than this time is assumed to be definitely frozen and is not allowed to be changed anymore.
	 * Smaller values makes garbage-collection more aggressive but risk to lose consensus with the peers
	 * in case of large history changes.
	 * 
	 * @return the maximal history change time (in milliseconds); if negative, changes of history are
	 *         always allowed, without any limit
	 */
	long getMaximalHistoryChangeTime();
}