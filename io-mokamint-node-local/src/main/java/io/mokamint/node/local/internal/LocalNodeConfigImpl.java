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

import java.io.FileNotFoundException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.NoSuchAlgorithmException;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import com.moandjiezana.toml.Toml;

import io.hotmoka.annotations.Immutable;
import io.mokamint.node.AbstractConsensusConfig;
import io.mokamint.node.AbstractConsensusConfigBuilder;
import io.mokamint.node.api.Peer;
import io.mokamint.node.api.RestrictedNode;
import io.mokamint.node.local.api.LocalNodeConfig;
import io.mokamint.node.local.api.LocalNodeConfigBuilder;

/**
 * The configuration of a local Mokamint node.
 */
@Immutable
public class LocalNodeConfigImpl extends AbstractConsensusConfig<LocalNodeConfig, LocalNodeConfigBuilder> implements LocalNodeConfig {

	/**
	 * The path where the node's data will be persisted.
	 * It defaults to {@code mokamint-chain} in the current directory.
	 */
	public final Path dir;

	/**
	 * The maximal delay, in milliseconds, between a deadline request to the miners
	 * and the reception of the first deadline from the miners. After this threshold,
	 * deadlines might well arrive, but might get ignored by the node.
	 * It defaults to 20000.
	 */
	public final int deadlineWaitTimeout;

	/**
	 * The initial points of a miner, freshly connected to a node.
	 * It defaults to 1000.
	 */
	public final long minerInitialPoints;

	/**
	 * The points lost for punishment by a miner that timeouts
	 * at a request for a deadline. It defaults to 1.
	 */
	public final long minerPunishmentForTimeout;

	/**
	 * The points lost by a miner that provides an illegal deadline.
	 * It defaults to 500.
	 */
	public final long minerPunishmentForIllegalDeadline;

	/**
	 * The set of URIs of the initial peers that must be contacted at start-up
	 * and potentially end-up in the set of active peers.
	 * It defaults to the empty set.
	 */
	private final Set<URI> seeds;

	/**
	 * The maximum number of peers kept by a node. The actual number of peers can
	 * be larger only if peers are explicitly added as seeds or through the
	 * {@link RestrictedNode#add(Peer)} method.
	 * It defaults to 20.
	 */
	public final long maxPeers;

	/**
	 * The initial points of a peer, freshly added to a node.
	 * It defaults to 1000.
	 */
	public final long peerInitialPoints;

	/**
	 * The maximal difference (in milliseconds) between the local time of a node
	 * and of one of its peers. It defaults to 15,000 (15 seconds).
	 */
	public final int peerMaxTimeDifference;

	/**
	 * The points lost for punishment by a peer that does not answer to a ping request.
	 * It defaults to 1.
	 */
	public final long peerPunishmentForUnreachable;

	/**
	 * The time, in milliseconds, allowed to contact a peer. Beyond this threshold, the request timeouts.
	 * It defaults to 10,000 (ie, 10 seconds).
	 */
	public final int peerTimeout;

	/**
	 * The time interval, in milliseconds, between successive pings to a peer.
	 * Every time the peer does not answer, its points are reduced by {@link #peerPunishmentForUnreachable},
	 * until they reach zero and the peer is removed. During a successful ping, its peers are collected
	 * if they are useful for the node (for instance, if the node has too few peers).
	 * It defaults to 120,000 (ie, 2 minutes).
	 */
	public final int peerPingInterval;

	/**
	 * The time interval, in milliseconds, between successive broadcasts
	 * of a service open on a node. It defaults to 240,000 (ie, 4 minutes).
	 */
	public final int serviceBroadcastInterval;

	/**
	 * The size of the memory used to avoid whispering the same
	 * message again; higher numbers reduce the circulation of spurious messages.
	 * It defaults to 1000.
	 */
	public final int whisperingMemorySize;

	/**
	 * The size of the memory used to hold orphan nodes, that is, nodes received
	 * from the network but having no parent in the blockchain. Larger sizes allow for
	 * out of order reception of blocks, without synchronization. It defaults to 1000.
	 */
	public final int orphansMemorySize;

	/**
	 * The size of the mempool of the node, that is, the area
	 * of memory where incoming transactions are held before being verified and added to blocks.
	 */
	public final int mempoolSize;

	/**
	 * The size of the group of blocks whose hashes get downloaded
	 * in one shot during synchronization.
	 */
	public final int synchronizationGroupSize;

	/**
	 * The maximal time (in milliseconds) a block can be created in the future,
	 * from now (intended as network time now). Block verification will reject blocks created
	 * beyond this threshold. It defaults to 15,000 (15 seconds).
	 */
	public final long blockMaxTimeInTheFuture;

	/**
	 * The maximal history change time for the blockchain, in milliseconds. That is, part of the history older
	 * than this time is assumed to be definitely frozen and it is not allowed to be changed anymore.
	 * If negative, changes of history are always allowed, without any limit, which drastically reduces
	 * the opportunities for garbage-collection of the blocks' database and of the database for the states of the
	 * application, if the latter implements any garbage-collection strategy.
	 */
	public final long maximalHistoryChangeTime;

	/**
	 * Full constructor for the builder pattern.
	 */
	private LocalNodeConfigImpl(LocalNodeConfigBuilderImpl builder) {
		super(builder);

		this.dir = builder.dir;
		this.deadlineWaitTimeout = builder.deadlineWaitTimeout;
		this.minerInitialPoints = builder.minerInitialPoints;
		this.minerPunishmentForTimeout = builder.minerPunishmentForTimeout;
		this.minerPunishmentForIllegalDeadline = builder.minerPunishmentForIllegalDeadline;
		this.seeds = builder.seeds;
		this.maxPeers = builder.maxPeers;
		this.peerInitialPoints = builder.peerInitialPoints;
		this.peerMaxTimeDifference = builder.peerMaxTimeDifference;
		this.peerPunishmentForUnreachable = builder.peerPunishmentForUnreachable;
		this.peerTimeout = builder.peerTimeout;
		this.peerPingInterval = builder.peerPingInterval;
		this.serviceBroadcastInterval = builder.serviceBroadcastInterval;
		this.whisperingMemorySize = builder.whisperingMemorySize;
		this.orphansMemorySize = builder.orphansMemorySize;
		this.mempoolSize = builder.mempoolSize;
		this.synchronizationGroupSize = builder.synchronizationGroupSize;
		this.blockMaxTimeInTheFuture = builder.blockMaxTimeInTheFuture;
		this.maximalHistoryChangeTime = builder.maximalHistoryChangeTime;
	}

	@Override
	public Path getDir() {
		return dir;
	}

	@Override
	public int getDeadlineWaitTimeout() {
		return deadlineWaitTimeout;
	}

	@Override
	public long getMinerInitialPoints() {
		return minerInitialPoints;
	}

	@Override
	public long getMinerPunishmentForTimeout() {
		return minerPunishmentForTimeout;
	}

	@Override
	public long getMinerPunishmentForIllegalDeadline() {
		return minerPunishmentForIllegalDeadline;
	}

	@Override
	public Stream<URI> getSeeds() {
		return seeds.stream();
	}

	@Override
	public long getMaxPeers() {
		return maxPeers;
	}

	@Override
	public long getPeerInitialPoints() {
		return peerInitialPoints;
	}

	@Override
	public int getPeerMaxTimeDifference() {
		return peerMaxTimeDifference;
	}

	@Override
	public long getPeerPunishmentForUnreachable() {
		return peerPunishmentForUnreachable;
	}

	@Override
	public int getPeerTimeout() {
		return peerTimeout;
	}

	@Override
	public int getPeerPingInterval() {
		return peerPingInterval;
	}

	@Override
	public int getServiceBrodcastInterval() {
		return serviceBroadcastInterval;
	}

	@Override
	public int getWhisperingMemorySize() {
		return whisperingMemorySize;
	}

	@Override
	public int getOrphansMemorySize() {
		return orphansMemorySize;
	}

	@Override
	public int getMempoolSize() {
		return mempoolSize;
	}

	@Override
	public int getSynchronizationGroupSize() {
		return synchronizationGroupSize;
	}

	@Override
	public long getBlockMaxTimeInTheFuture() {
		return blockMaxTimeInTheFuture;
	}

	@Override
	public long getMaximalHistoryChangeTime() {
		return maximalHistoryChangeTime;
	}

	@Override
	public String toToml() {
		var sb = new StringBuilder(super.toToml());

		sb.append("\n");
		sb.append("## Local parameters\n");
		sb.append("\n");
		sb.append("# the path where the node's data will be persisted\n");
		sb.append("dir = \"" + dir + "\"\n");
		sb.append("\n");
		sb.append("# maximal milliseconds to wait between deadline request to the miners and first deadline reception\n");
		sb.append("deadline_wait_timeout = " + deadlineWaitTimeout + "\n");
		sb.append("\n");
		sb.append("# the initial points of a miner, freshly connected to a node\n");
		sb.append("miner_initial_points = " + minerInitialPoints + "\n");
		sb.append("\n");
		sb.append("# the points that a miner loses as punishment for a timeout at a request for a deadline\n");
		sb.append("miner_punishment_for_timeout = " + minerPunishmentForTimeout + "\n");
		sb.append("\n");
		sb.append("# the points that a miner loses as punishment for providing an illegal deadline\n");
		sb.append("miner_punishment_for_illegal_deadline = " + minerPunishmentForIllegalDeadline + "\n");
		sb.append("\n");
		sb.append("# the URIs of the initial peers, that will always get added to the previous set of peers\n");
		sb.append("# (if any) and contacted at start-up\n");
		sb.append("seeds = " + getSeeds().map(uri -> "\"" + uri + "\"").collect(Collectors.joining(",", "[", "]")) + "\n");
		sb.append("\n");
		sb.append("# the maximum amount of peers of a node; their actual number can be larger\n");
		sb.append("# only if peers are explicitly added as seeds or through the addPeer() method\n");
		sb.append("# of the restricted API of the node\n");
		sb.append("max_peers = " + maxPeers + "\n");
		sb.append("\n");
		sb.append("# the initial points of a peer, freshly added to a node\n");
		sb.append("peer_initial_points = " + peerInitialPoints + "\n");
		sb.append("\n");
		sb.append("# the maximal time difference (in milliseconds) between a node and each of its peers\n");
		sb.append("peer_max_time_difference = " + peerMaxTimeDifference + "\n");
		sb.append("\n");
		sb.append("# the points that a peer loses as punishment for not answering a ping\n");
		sb.append("peer_punishment_for_unreachable = " + peerPunishmentForUnreachable + "\n");
		sb.append("\n");
		sb.append("# the time (in milliseconds) for communications to the peers\n");
		sb.append("peer_timeout = " + peerTimeout + "\n");
		sb.append("\n");
		sb.append("# the time, in milliseconds, between successive pings to a peer\n");
		sb.append("peer_ping_interval = " + peerPingInterval + "\n");
		sb.append("\n");
		sb.append("# the time, in milliseconds, between successive broadcasts of a service\n");
		sb.append("service_broadcast_interval = " + serviceBroadcastInterval + "\n");
		sb.append("\n");
		sb.append("# the size of the memory used to avoid whispering the same message again\n");
		sb.append("whispering_memory_size = " + whisperingMemorySize + "\n");
		sb.append("\n");
		sb.append("# the size of the memory used to hold orphan nodes\n");
		sb.append("orphans_memory_size = " + orphansMemorySize + "\n");
		sb.append("\n");
		sb.append("# the size of the memory used to hold incoming transactions before they get put into blocks\n");
		sb.append("mempool_size = " + mempoolSize + "\n");
		sb.append("\n");
		sb.append("# the size of the group of blocks whose hashes get downloaded in one shot during synchronization\n");
		sb.append("synchronization_group_size = " + synchronizationGroupSize + "\n");
		sb.append("\n");
		sb.append("# the maximal creation time in the future (in milliseconds) of a block\n");
		sb.append("block_max_time_in_the_future = " + blockMaxTimeInTheFuture + "\n");
		sb.append("\n");
		sb.append("# the maximal time (in milliseconds) that history can be changed before being considered as definitely frozen;\n");
		sb.append("# a negative value means that the history is always allowed to be changed, without limits\n");
		sb.append("maximal_history_change_time = " + maximalHistoryChangeTime + "\n");

		return sb.toString();
	}

	@Override
	public LocalNodeConfigBuilder toBuilder() {
		return new LocalNodeConfigBuilderImpl(this);
	}

	@Override
	public boolean equals(Object other) {
		if (super.equals(other)) {
			var otherConfig = (LocalNodeConfigImpl) other;

			return dir.equals(otherConfig.dir) &&
				deadlineWaitTimeout == otherConfig.deadlineWaitTimeout &&
				minerInitialPoints == otherConfig.minerInitialPoints &&
				minerPunishmentForTimeout == otherConfig.minerPunishmentForTimeout &&
				minerPunishmentForIllegalDeadline == otherConfig.minerPunishmentForIllegalDeadline &&
				seeds.equals(otherConfig.seeds) &&
				maxPeers == otherConfig.maxPeers &&
				peerInitialPoints == otherConfig.peerInitialPoints &&
				peerMaxTimeDifference == otherConfig.peerMaxTimeDifference &&
				peerPunishmentForUnreachable == otherConfig.peerPunishmentForUnreachable &&
				peerTimeout == otherConfig.peerTimeout &&
				peerPingInterval == otherConfig.peerPingInterval &&
				serviceBroadcastInterval == otherConfig.serviceBroadcastInterval &&
				whisperingMemorySize == otherConfig.whisperingMemorySize &&
				orphansMemorySize == otherConfig.orphansMemorySize &&
				mempoolSize == otherConfig.mempoolSize &&
				synchronizationGroupSize == otherConfig.synchronizationGroupSize &&
				blockMaxTimeInTheFuture == otherConfig.blockMaxTimeInTheFuture &&
				maximalHistoryChangeTime == otherConfig.maximalHistoryChangeTime;
		}
		else
			return false;
	}

	/**
	 * The builder of a configuration object.
	 */
	public static class LocalNodeConfigBuilderImpl extends AbstractConsensusConfigBuilder<LocalNodeConfig, LocalNodeConfigBuilder> implements LocalNodeConfigBuilder {
		private Path dir = Paths.get("mokamint-chain");
		private int deadlineWaitTimeout = 20_000;
		private long minerInitialPoints = 1000L;
		private long minerPunishmentForTimeout = 1L;
		private long minerPunishmentForIllegalDeadline = 500L;
		private final Set<URI> seeds = new HashSet<>();
		private long maxPeers = 20L;
		private long peerInitialPoints = 1000L;
		private int peerMaxTimeDifference = 15000;
		private long peerPunishmentForUnreachable = 1L;
		private int peerTimeout = 10000;
		private int peerPingInterval = 120_000;
		private int serviceBroadcastInterval = 240_000;
		private int whisperingMemorySize = 1000;
		private int orphansMemorySize = 1000;
		private int mempoolSize = 100_000;
		private int synchronizationGroupSize = 500;
		private long blockMaxTimeInTheFuture = 15000L;
		private long maximalHistoryChangeTime = 60 * 60 * 1000; // one hour

		/**
		 * Creates a configuration builder with initial default values.
		 * 
		 * @throws NoSuchAlgorithmException if some hashing algorithm is not available
		 */
		public LocalNodeConfigBuilderImpl() throws NoSuchAlgorithmException {
		}

		private LocalNodeConfigBuilderImpl(Toml toml) throws NoSuchAlgorithmException, FileNotFoundException, URISyntaxException {
			super(toml);

			var dir = toml.getString("dir");
			if (dir != null)
				setDir(Paths.get(dir));

			var deadlineWaitTimeout = toml.getLong("deadline_wait_timeout");
			if (deadlineWaitTimeout != null)
				setDeadlineWaitTimeout(deadlineWaitTimeout);

			var minerInitialPoints = toml.getLong("miner_initial_points");
			if (minerInitialPoints != null)
				setMinerInitialPoints(minerInitialPoints);

			var minerPunishmentForTimeout = toml.getLong("miner_punishment_for_timeout");
			if (minerPunishmentForTimeout != null)
				setMinerPunishmentForTimeout(minerPunishmentForTimeout);

			var minerPunishmentForIllegalDeadline = toml.getLong("miner_punishment_for_illegal_deadline");
			if (minerPunishmentForIllegalDeadline != null)
				setMinerPunishmentForIllegalDeadline(minerPunishmentForIllegalDeadline);

			List<String> seeds = toml.getList("seeds");
			if (seeds != null)
				for (var uri: seeds)
					this.seeds.add(new URI(uri));

			var maxPeers = toml.getLong("max_peers");
			if (maxPeers != null)
				setMaxPeers(maxPeers);

			var maxCandidatePeers = toml.getLong("max_candidate_peers");
			if (maxCandidatePeers != null)
				setMaxPeers(maxCandidatePeers);

			var peerInitialPoints = toml.getLong("peer_initial_points");
			if (peerInitialPoints != null)
				setPeerInitialPoints(peerInitialPoints);

			var peerMaxTimeDifference = toml.getLong("peer_max_time_difference");
			if (peerMaxTimeDifference != null)
				setPeerMaxTimeDifference(peerMaxTimeDifference);

			var peerPunishmentForUnreachable = toml.getLong("peer_punishment_for_unreachable");
			if (peerPunishmentForUnreachable != null)
				setPeerPunishmentForUnreachable(peerPunishmentForUnreachable);

			var peerTimeout = toml.getLong("peer_timeout");
			if (peerTimeout != null)
				setPeerTimeout(peerTimeout);

			var peerPingInterval = toml.getLong("peer_ping_interval");
			if (peerPingInterval != null)
				setPeerPingInterval(peerPingInterval);

			var serviceBroadcastInterval = toml.getLong("service_broadcast_interval");
			if (serviceBroadcastInterval != null)
				setServiceBroadcastInterval(serviceBroadcastInterval);

			var whisperingMemorySize = toml.getLong("whispering_memory_size");
			if (whisperingMemorySize != null)
				setWhisperingMemorySize(whisperingMemorySize);

			var orphansMemorySize = toml.getLong("orphans_memory_size");
			if (orphansMemorySize != null)
				setOrphansMemorySize(orphansMemorySize);

			var mempoolSize = toml.getLong("mempool_size");
			if (mempoolSize != null)
				setMempoolSize(mempoolSize);

			var synchronizationGroupSize = toml.getLong("synchronization_group_size");
			if (synchronizationGroupSize != null)
				setSynchronizationGroupSize(synchronizationGroupSize);

			var blockMaxTimeInTheFuture = toml.getLong("block_max_time_in_the_future");
			if (blockMaxTimeInTheFuture != null)
				setBlockMaxTimeInTheFuture(blockMaxTimeInTheFuture);

			var maximalHistoryChangeTime = toml.getLong("maximal_history_change_time");
			if (maximalHistoryChangeTime != null)
				setMaximalHistoryChangeTime(maximalHistoryChangeTime);
		}

		/**
		 * Creates a builder by reading the properties of the given TOML file and sets them for
		 * the corresponding fields of this builder.
		 * 
		 * @param toml the file
		 * @throws FileNotFoundException if the file cannot be found
		 * @throws URISyntaxException if the file refers to a URI with a wrong syntax
		 * @throws NoSuchAlgorithmException if the file refers to a hashing algorithm that is not available
		 */
		public LocalNodeConfigBuilderImpl(Path toml) throws FileNotFoundException, NoSuchAlgorithmException, URISyntaxException {
			this(readToml(toml));
		}

		/**
		 * Creates a builder with properties initialized to those of the given configuration object.
		 * 
		 * @param config the configuration object
		 */
		private LocalNodeConfigBuilderImpl(LocalNodeConfigImpl config) {
			super(config);

			this.dir = config.dir;
			this.deadlineWaitTimeout = config.deadlineWaitTimeout;
			this.minerInitialPoints = config.minerInitialPoints;
			this.minerPunishmentForTimeout = config.minerPunishmentForTimeout;
			this.minerPunishmentForIllegalDeadline = config.minerPunishmentForIllegalDeadline;
			this.seeds.addAll(config.seeds);
			this.maxPeers = config.maxPeers;
			this.peerInitialPoints = config.peerInitialPoints;
			this.peerMaxTimeDifference = config.peerMaxTimeDifference;
			this.peerPunishmentForUnreachable = config.peerPunishmentForUnreachable;
			this.peerTimeout = config.peerTimeout;
			this.peerPingInterval = config.peerPingInterval;
			this.serviceBroadcastInterval = config.serviceBroadcastInterval;
			this.whisperingMemorySize = config.whisperingMemorySize;
			this.orphansMemorySize = config.orphansMemorySize;
			this.mempoolSize = config.mempoolSize;
			this.synchronizationGroupSize = config.synchronizationGroupSize;
			this.blockMaxTimeInTheFuture = config.blockMaxTimeInTheFuture;
			this.maximalHistoryChangeTime = config.maximalHistoryChangeTime;
		}

		@Override
		public LocalNodeConfigBuilder setDir(Path dir) {
			Objects.requireNonNull(dir);
			this.dir = dir;
			return getThis();
		}

		@Override
		public LocalNodeConfigBuilder setDeadlineWaitTimeout(int deadlineWaitTimeout) {
			if (deadlineWaitTimeout < 0)
				throw new IllegalArgumentException("deadlineWaitTimeout must be non-negative");

			this.deadlineWaitTimeout = deadlineWaitTimeout;
			return getThis();
		}

		private LocalNodeConfigBuilder setDeadlineWaitTimeout(long deadlineWaitTimeout) {
			if (deadlineWaitTimeout < 0 || deadlineWaitTimeout > Integer.MAX_VALUE)
				throw new IllegalArgumentException("deadlineWaitTimeout must be between 0 and " + Integer.MAX_VALUE + " inclusive");

			this.deadlineWaitTimeout = (int) deadlineWaitTimeout;
			return getThis();
		}

		@Override
		public LocalNodeConfigBuilder setMinerInitialPoints(long minerInitialPoints) {
			if (minerInitialPoints <= 0L)
				throw new IllegalArgumentException("minerInitialPoints must be positive");

			this.minerInitialPoints = minerInitialPoints;
			return getThis();
		}

		@Override
		public LocalNodeConfigBuilder setMinerPunishmentForTimeout(long minerPunishmentForTimeout) {
			if (minerPunishmentForTimeout < 0L)
				throw new IllegalArgumentException("minerPunishmentForTimeout must be non-negative");

			this.minerPunishmentForTimeout = minerPunishmentForTimeout;
			return getThis();
		}

		@Override
		public LocalNodeConfigBuilder setMinerPunishmentForIllegalDeadline(long minerPunishmentForIllegalDeadline) {
			if (minerPunishmentForIllegalDeadline < 0L)
				throw new IllegalArgumentException("minerPunishmentForIllegalDeadline must be non-negative");

			this.minerPunishmentForIllegalDeadline = minerPunishmentForIllegalDeadline;
			return getThis();
		}

		@Override
		public LocalNodeConfigBuilder addSeed(URI uri) {
			seeds.add(Objects.requireNonNull(uri));
			return getThis();
		}

		@Override
		public LocalNodeConfigBuilder setMaxPeers(long maxPeers) {
			if (maxPeers < 0L)
				throw new IllegalArgumentException("maxPeers must be non-negative");

			this.maxPeers = maxPeers;
			return getThis();
		}

		@Override
		public LocalNodeConfigBuilder setPeerInitialPoints(long peerInitialPoints) {
			if (peerInitialPoints <= 0L)
				throw new IllegalArgumentException("peerInitialPoints must be positive");

			this.peerInitialPoints = peerInitialPoints;
			return getThis();
		}

		@Override
		public LocalNodeConfigBuilder setPeerMaxTimeDifference(int peerMaxTimeDifference) {
			if (peerMaxTimeDifference < 0L)
				throw new IllegalArgumentException("peerMaxTimeDifference must be non-negative");

			this.peerMaxTimeDifference = peerMaxTimeDifference;
			return getThis();
		}

		private LocalNodeConfigBuilder setPeerMaxTimeDifference(long peerMaxTimeDifference) {
			if (peerMaxTimeDifference < 0L || peerMaxTimeDifference > Integer.MAX_VALUE)
				throw new IllegalArgumentException("peerMaxTimeDifference must be between 0 and " + Integer.MAX_VALUE + " inclusive");

			return setPeerMaxTimeDifference((int) peerMaxTimeDifference);
		}

		@Override
		public LocalNodeConfigBuilder setPeerPunishmentForUnreachable(long peerPunishmentForUnreachable) {
			if (peerPunishmentForUnreachable < 0L)
				throw new IllegalArgumentException("peerPunishmentForUnreachable must be non-negative");

			this.peerPunishmentForUnreachable = peerPunishmentForUnreachable;
			return getThis();
		}

		private LocalNodeConfigBuilder setPeerTimeout(long peerTimeout) {
			if (peerTimeout > Integer.MAX_VALUE)
				throw new IllegalArgumentException("peerTimeout cannot be larger than " + Integer.MAX_VALUE);

			return setPeerTimeout((int) peerTimeout);
		}

		@Override
		public LocalNodeConfigBuilder setPeerTimeout(int peerTimeout) {
			if (peerTimeout < 0)
				throw new IllegalArgumentException("peerTimeout must be non-negative");

			this.peerTimeout = peerTimeout;
			return getThis();
		}

		@Override
		public LocalNodeConfigBuilder setPeerPingInterval(int peerPingInterval) {
			this.peerPingInterval = peerPingInterval;
			return getThis();
		}

		private LocalNodeConfigBuilder setPeerPingInterval(long peerPingInterval) {
			if (peerPingInterval < Integer.MIN_VALUE || peerPingInterval > Integer.MAX_VALUE)
				throw new IllegalArgumentException("peerTimeout must be between " + Integer.MIN_VALUE + " and " + Integer.MAX_VALUE + " inclusive");

			return setPeerPingInterval((int) peerPingInterval);
		}

		@Override
		public LocalNodeConfigBuilder setServiceBroadcastInterval(int serviceBroadcastInterval) {
			this.serviceBroadcastInterval = serviceBroadcastInterval;
			return getThis();
		}

		private LocalNodeConfigBuilder setServiceBroadcastInterval(long serviceBroadcastInterval) {
			if (serviceBroadcastInterval < Integer.MIN_VALUE || serviceBroadcastInterval > Integer.MAX_VALUE)
				throw new IllegalArgumentException("serviceBroadcastInterval must be between " + Integer.MIN_VALUE + " and " + Integer.MAX_VALUE + " inclusive");

			return setServiceBroadcastInterval((int) serviceBroadcastInterval);
		}

		@Override
		public LocalNodeConfigBuilder setWhisperingMemorySize(int whisperingMemorySize) {
			if (whisperingMemorySize < 0)
				throw new IllegalArgumentException("whisperingMemorySize must be non-negative");

			this.whisperingMemorySize = whisperingMemorySize;
			return getThis();
		}

		private LocalNodeConfigBuilder setWhisperingMemorySize(long whisperingMemorySize) {
			if (whisperingMemorySize < 0 || whisperingMemorySize > Integer.MAX_VALUE)
				throw new IllegalArgumentException("whisperingMemorySize must be between 0 and " + Integer.MAX_VALUE + " inclusive");

			return setWhisperingMemorySize((int) whisperingMemorySize);
		}

		@Override
		public LocalNodeConfigBuilder setOrphansMemorySize(int orphansMemorySize) {
			if (orphansMemorySize < 0)
				throw new IllegalArgumentException("orphansMemorySize must be non-negative");

			this.orphansMemorySize = orphansMemorySize;
			return getThis();
		}

		private LocalNodeConfigBuilder setOrphansMemorySize(long orphansMemorySize) {
			if (orphansMemorySize < 0 || orphansMemorySize > Integer.MAX_VALUE)
				throw new IllegalArgumentException("orphansMemorySize must be between 0 and " + Integer.MAX_VALUE + " inclusive");

			return setOrphansMemorySize((int) orphansMemorySize);
		}

		@Override
		public LocalNodeConfigBuilder setMempoolSize(int mempoolSize) {
			if (mempoolSize < 0)
				throw new IllegalArgumentException("mempoolSize must be non-negative");

			this.mempoolSize = mempoolSize;
			return getThis();
		}

		private LocalNodeConfigBuilder setMempoolSize(long mempoolSize) {
			if (mempoolSize < 0 || mempoolSize > Integer.MAX_VALUE)
				throw new IllegalArgumentException("mempoolSize must be between 0 and " + Integer.MAX_VALUE + " inclusive");

			return setMempoolSize((int) mempoolSize);
		}

		@Override
		public LocalNodeConfigBuilder setSynchronizationGroupSize(int synchronizationGroupSize) {
			if (synchronizationGroupSize <= 0)
				throw new IllegalArgumentException("synchronizationGroupSize must be positive");

			this.synchronizationGroupSize = synchronizationGroupSize;
			return getThis();
		}

		private LocalNodeConfigBuilder setSynchronizationGroupSize(long synchronizationGroupSize) {
			if (synchronizationGroupSize <= 0 || synchronizationGroupSize > Integer.MAX_VALUE)
				throw new IllegalArgumentException("synchronizationGroupSize must be between 1 and " + Integer.MAX_VALUE + " inclusive");

			return setSynchronizationGroupSize((int) synchronizationGroupSize);
		}

		@Override
		public LocalNodeConfigBuilder setBlockMaxTimeInTheFuture(long blockMaxTimeInTheFuture) {
			if (blockMaxTimeInTheFuture < 0L)
				throw new IllegalArgumentException("blockMaxTimeInTheFuture must be non-negative");
		
			this.blockMaxTimeInTheFuture = blockMaxTimeInTheFuture;
			return getThis();
		}

		@Override
		public LocalNodeConfigBuilder setMaximalHistoryChangeTime(long maximalHistoryChangeTime) {
			this.maximalHistoryChangeTime = maximalHistoryChangeTime;
			return getThis();
		}

		@Override
		public LocalNodeConfig build() {
			return new LocalNodeConfigImpl(this);
		}

		@Override
		protected LocalNodeConfigBuilder getThis() {
			return this;
		}
	}
}