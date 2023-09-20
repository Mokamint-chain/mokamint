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

package io.mokamint.node.local;

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
	public final long deadlineWaitTimeout;

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
	public final long peerMaxTimeDifference;

	/**
	 * The points lost for punishment by a peer that does not answer to a ping request.
	 * It defaults to 1.
	 */
	public final long peerPunishmentForUnreachable;

	/**
	 * The time, in milliseconds, allowed to contact a peer. Beyond this threshold, the request timeouts.
	 * It defaults to 10,000 (ie, 10 seconds).
	 */
	public final long peerTimeout;

	/**
	 * The time interval, in milliseconds, between successive pings to a peer.
	 * Every time the peer does not answer, its points are reduced by {@link #peerPunishmentForUnreachable},
	 * until they reach zero and the peer is removed. During a successful ping, its peers are collected
	 * if they are useful for the node (for instance, if the node has too few peers).
	 * It defaults to 120,000 (ie, 2 minutes).
	 */
	public final long peerPingInterval;

	/**
	 * The size of the memory used to avoid whispering the same
	 * message again; higher numbers reduce the circulation of spurious messages.
	 * It defaults to 1000.
	 */
	public final long whisperingMemorySize;

	/**
	 * The maximal time (in milliseconds) a block can be created in the future,
	 * from now (intended as network time now). Block verification will reject blocks created
	 * beyond this threshold. It defaults to 15,000 (15 seconds).
	 */
	public final long blockMaxTimeInTheFuture;

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
		this.whisperingMemorySize = builder.whisperingMemorySize;
		this.blockMaxTimeInTheFuture = builder.blockMaxTimeInTheFuture;
	}

	@Override
	public Path getDir() {
		return dir;
	}

	@Override
	public long getDeadlineWaitTimeout() {
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
	public long getPeerMaxTimeDifference() {
		return peerMaxTimeDifference;
	}

	@Override
	public long getPeerPunishmentForUnreachable() {
		return peerPunishmentForUnreachable;
	}

	@Override
	public long getPeerTimeout() {
		return peerTimeout;
	}

	@Override
	public long getPeerPingInterval() {
		return peerPingInterval;
	}

	@Override
	public long getWhisperingMemorySize() {
		return whisperingMemorySize;
	}

	@Override
	public long getBlockMaxTimeInTheFuture() {
		return blockMaxTimeInTheFuture;
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
		sb.append("# the size of the memory used to avoid whispering the same message again\n");
		sb.append("whispering_memory_size = " + whisperingMemorySize + "\n");
		sb.append("\n");
		sb.append("# the maximal creation time in the future (in milliseconds) of a block\n");
		sb.append("block_max_time_in_the_future = " + blockMaxTimeInTheFuture + "\n");

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
				whisperingMemorySize == otherConfig.whisperingMemorySize &&
				blockMaxTimeInTheFuture == otherConfig.blockMaxTimeInTheFuture;
		}
		else
			return false;
	}

	/**
	 * The builder of a configuration object.
	 */
	public static class LocalNodeConfigBuilderImpl extends AbstractConsensusConfigBuilder<LocalNodeConfig, LocalNodeConfigBuilder> implements LocalNodeConfigBuilder {
		private Path dir = Paths.get("mokamint-chain");
		private long deadlineWaitTimeout = 20000L;
		private long minerInitialPoints = 1000L;
		private long minerPunishmentForTimeout = 1L;
		private long minerPunishmentForIllegalDeadline = 500L;
		private final Set<URI> seeds = new HashSet<>();
		private long maxPeers = 20L;
		private long peerInitialPoints = 1000L;
		private long peerMaxTimeDifference = 15000L;
		private long peerPunishmentForUnreachable = 1L;
		private long peerTimeout = 10000L;
		private long peerPingInterval = 120000L;
		private long whisperingMemorySize = 1000L;
		private long blockMaxTimeInTheFuture = 15000L;

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

			var whisperingMemorySize = toml.getLong("whispering_memory_size");
			if (whisperingMemorySize != null)
				setWhisperingMemorySize(whisperingMemorySize);

			var blockMaxTimeInTheFuture = toml.getLong("block_max_time_in_the_future");
			if (blockMaxTimeInTheFuture != null)
				setBlockMaxTimeInTheFuture(blockMaxTimeInTheFuture);
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
			this.whisperingMemorySize = config.whisperingMemorySize;
			this.blockMaxTimeInTheFuture = config.blockMaxTimeInTheFuture;
		}

		@Override
		public LocalNodeConfigBuilder setDir(Path dir) {
			Objects.requireNonNull(dir);
			this.dir = dir;
			return getThis();
		}

		@Override
		public LocalNodeConfigBuilder setDeadlineWaitTimeout(long deadlineWaitTimeout) {
			if (deadlineWaitTimeout < 0L)
				throw new IllegalArgumentException("deadlineWaitTimeout must be non-negative");

			this.deadlineWaitTimeout = deadlineWaitTimeout;
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
			Objects.requireNonNull(uri);
			seeds.add(uri);
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
		public LocalNodeConfigBuilder setPeerMaxTimeDifference(long peerMaxTimeDifference) {
			if (peerMaxTimeDifference < 0L)
				throw new IllegalArgumentException("peerMaxTimeDifference must be non-negative");

			this.peerMaxTimeDifference = peerMaxTimeDifference;
			return getThis();
		}

		@Override
		public LocalNodeConfigBuilder setPeerPunishmentForUnreachable(long peerPunishmentForUnreachable) {
			if (peerPunishmentForUnreachable < 0L)
				throw new IllegalArgumentException("peerPunishmentForUnreachable must be non-negative");

			this.peerPunishmentForUnreachable = peerPunishmentForUnreachable;
			return getThis();
		}

		@Override
		public LocalNodeConfigBuilder setPeerTimeout(long peerTimeout) {
			if (peerTimeout < 0L)
				throw new IllegalArgumentException("peerTimeout must be non-negative");

			this.peerTimeout = peerTimeout;
			return getThis();
		}

		@Override
		public LocalNodeConfigBuilder setPeerPingInterval(long peerPingInterval) {
			if (peerPingInterval < 0L)
				throw new IllegalArgumentException("peerPingInterval must be non-negative");

			this.peerPingInterval = peerPingInterval;
			return getThis();
		}

		@Override
		public LocalNodeConfigBuilder setWhisperingMemorySize(long whisperingMemorySize) {
			if (whisperingMemorySize < 0L)
				throw new IllegalArgumentException("whisperingMemorySize must be non-negative");

			this.whisperingMemorySize = whisperingMemorySize;
			return getThis();
		}

		@Override
		public LocalNodeConfigBuilder setBlockMaxTimeInTheFuture(long blockMaxTimeInTheFuture) {
			if (blockMaxTimeInTheFuture < 0L)
				throw new IllegalArgumentException("blockMaxTimeInTheFuture must be non-negative");
		
			this.blockMaxTimeInTheFuture = blockMaxTimeInTheFuture;
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