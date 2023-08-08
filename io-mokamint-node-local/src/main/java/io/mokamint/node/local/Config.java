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
import io.mokamint.node.AbstractConfig;
import io.mokamint.node.AbstractConfigBuilder;
import io.mokamint.node.api.Peer;
import io.mokamint.node.api.RestrictedNode;

/**
 * The configuration of a local Mokamint node.
 */
@Immutable
public class Config extends AbstractConfig {

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
	 * Yields the URIs of the initial peers that must be contacted at start-up
	 * and potentially end-up in the set of active peers. It defaults to the empty set.
	 * 
	 * @return the set of URIs of initial peers
	 */
	public Stream<URI> seeds() {
		return seeds.stream();
	}

	/**
	 * The maximum number of peers kept by a node. The actual number of peers can
	 * be larger only if peers are explicitly added as seeds or through the
	 * {@link RestrictedNode#addPeer(Peer)} method.
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
	 * Full constructor for the builder pattern.
	 */
	private Config(Builder builder) {
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
	}

	@Override
	public String toToml() {
		StringBuilder sb = new StringBuilder(super.toToml());
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
		sb.append("seeds = " + seeds().map(uri -> "\"" + uri + "\"").collect(Collectors.joining(",", "[", "]")) + "\n");
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
		sb.append("# time, in milliseconds, between successive pings to a peer\n");
		sb.append("peer_ping_interval = " + peerPingInterval + "\n");
		sb.append("\n");
		sb.append("# the size of the memory used to avoid whispering the same message again\n");
		sb.append("whispering_memory_size = " + whisperingMemorySize + "\n");

		return sb.toString();
	}

	@Override
	public boolean equals(Object other) {
		if (super.equals(other)) {
			var otherConfig = (Config) other;
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
				whisperingMemorySize == otherConfig.whisperingMemorySize;
		}
		else
			return false;
	}

	/**
	 * The builder of a configuration object.
	 */
	public static class Builder extends AbstractConfigBuilder<Builder> {
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

		private Builder() throws NoSuchAlgorithmException {
		}

		private Builder(Toml toml) throws NoSuchAlgorithmException, FileNotFoundException, URISyntaxException {
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
		}

		/**
		 * Creates a builder containing default data.
		 * 
		 * @return the builder
		 * @throws NoSuchAlgorithmException if some hashing algorithm used in the default comfiguration is not available
		 */
		public static Builder defaults() throws NoSuchAlgorithmException {
			return new Builder();
		}

		/**
		 * Creates a builder from the given TOML configuration file.
		 * The resulting builder will contain the information in the file,
		 * and use defaults for the data not contained in the file.
		 * 
		 * @param path the path to the TOML file
		 * @return the builder
		 * @throws FileNotFoundException if {@code path} cannot be found
		 * @throws NoSuchAlgorithmException if the configuration file refers to some non-available hashing algorithm
		 * @throws URISyntaxException if the configuration file refers to a URI with wrong syntax
		 */
		public static Builder load(Path path) throws NoSuchAlgorithmException, FileNotFoundException, URISyntaxException {
			return new Builder(readToml(path));
		}

		/**
		 * Sets the directory where the node's data will be persisted.
		 * It defaults to {@code mokamint-chain} in the current directory.
		 * 
		 * @param dir the directory
		 * @return this builder
		 */
		public Builder setDir(Path dir) {
			Objects.requireNonNull(dir);
			this.dir = dir;
			return this;
		}

		/**
		 * Sets the maximal delay, in milliseconds, between the deadline request to the miners
		 * and the reception of the first deadline from the miners. This defaults to 20000.
		 * 
		 * @param deadlineWaitTimeout the wait time, in milliseconds
		 * @return this builder
		 */
		public Builder setDeadlineWaitTimeout(long deadlineWaitTimeout) {
			if (deadlineWaitTimeout < 0L)
				throw new IllegalArgumentException("deadlineWaitTimeout must be non-negative");

			this.deadlineWaitTimeout = deadlineWaitTimeout;
			return this;
		}

		/**
		 * Sets the initial points of a miner freshly connected to the node.
		 * These points might be reduced for punishment. If they reach zero, the miner
		 * gets disconnected. This defaults to 1000.
		 * 
		 * @param minerInitialPoints the initial points
		 * @return this builder
		 */
		public Builder setMinerInitialPoints(long minerInitialPoints) {
			if (minerInitialPoints <= 0L)
				throw new IllegalArgumentException("minerInitialPoints must be positive");

			this.minerInitialPoints = minerInitialPoints;
			return this;
		}

		/**
		 * Sets the points lost by a miner, as punishment for timeout
		 * at a request for a new deadline. This defaults to 1.
		 * 
		 * @param minerPunishmentForTimeout the points
		 * @return this builder
		 */
		public Builder setMinerPunishmentForTimeout(long minerPunishmentForTimeout) {
			if (minerPunishmentForTimeout < 0L)
				throw new IllegalArgumentException("minerPunishmentForTimeout must be non-negative");

			this.minerPunishmentForTimeout = minerPunishmentForTimeout;
			return this;
		}

		/**
		 * Sets the points lost by a miner, as punishment for providing an illegal deadline.
		 * This defaults to 500.
		 * 
		 * @param minerPunishmentForIllegalDeadline the points
		 * @return this builder
		 */
		public Builder setMinerPunishmentForIllegalDeadline(long minerPunishmentForIllegalDeadline) {
			if (minerPunishmentForIllegalDeadline < 0L)
				throw new IllegalArgumentException("minerPunishmentForIllegalDeadline must be non-negative");

			this.minerPunishmentForIllegalDeadline = minerPunishmentForIllegalDeadline;
			return this;
		}

		/**
		 * Adds the given URI to those of the initial peers (called seeds).
		 * Such peers are contacted at start-up.
		 * 
		 * @param uri the URI to add
		 * @return this builder
		 */
		public Builder addSeed(URI uri) {
			Objects.requireNonNull(uri);
			seeds.add(uri);
			return this;
		}

		/**
		 * Sets the maximum number of peers kept by a node. The actual number of peers can
		 * be larger only if peers are explicitly added as seeds or through the
		 * {@link RestrictedNode#addPeer(Peer)} method.
		 * It defaults to 20.
		 * 
		 * @param maxPeers the maximum number of peers
		 * @return this builder
		 */
		public Builder setMaxPeers(long maxPeers) {
			if (maxPeers < 0L)
				throw new IllegalArgumentException("maxPeers must be non-negative");

			this.maxPeers = maxPeers;
			return this;
		}

		/**
		 * Sets the initial points of a peer, freshly added to a node.
		 * These points might be reduced for punishment. If they reach zero, the peer
		 * gets disconnected. This defaults to 1000.
		 * 
		 * @param peerInitialPoints the initial points
		 * @return this builder
		 */
		public Builder setPeerInitialPoints(long peerInitialPoints) {
			if (peerInitialPoints <= 0L)
				throw new IllegalArgumentException("peerInitialPoints must be positive");

			this.peerInitialPoints = peerInitialPoints;
			return this;
		}

		/**
		 * Sets the maximal difference (in milliseconds) between the local time of a node
		 * and of one of its peers. This defaults to 15,000 (15 seconds).
		 * 
		 * @param peerMaxTimeDifference the maximal time difference (in milliseconds)
		 * @return this builder
		 */
		public Builder setPeerMaxTimeDifference(long peerMaxTimeDifference) {
			if (peerMaxTimeDifference < 0L)
				throw new IllegalArgumentException("peerMaxTimeDifference must be non-negative");

			this.peerMaxTimeDifference = peerMaxTimeDifference;
			return this;
		}

		/**
		 * Sets the points lost by a peer, as punishment for not answering a ping.
		 * This defaults to 1.
		 * 
		 * @param peerPunishmentForUnreachable the points
		 * @return this builder
		 */
		public Builder setPeerPunishmentForUnreachable(long peerPunishmentForUnreachable) {
			if (peerPunishmentForUnreachable < 0L)
				throw new IllegalArgumentException("peerPunishmentForUnreachable must be non-negative");

			this.peerPunishmentForUnreachable = peerPunishmentForUnreachable;
			return this;
		}

		/**
		 * Sets the time, in milliseconds, allowed to contact a peer. Beyond this threshold, the request timeouts.
		 * 
		 * @param peerTimeout the timeout
		 * @return this builder
		 */
		public Builder setPeerTimeout(long peerTimeout) {
			if (peerTimeout < 0L)
				throw new IllegalArgumentException("peerTimeout must be non-negative");

			this.peerTimeout = peerTimeout;
			return this;
		}

		/**
		 * Sets the time interval, in milliseconds, between successive pings to a peer.
		 * Every time the peer does not answer, its points are reduced by {@link #peerPunishmentForUnreachable},
		 * until they reach zero and the peer is removed.  During a successful ping, its peers are collected
		 * if they are useful for the node (for instance, if the node has too few peers).
		 * 
		 * @param peerPingInterval the time interval
		 * @return this builder
		 */
		public Builder setPeerPingInterval(long peerPingInterval) {
			if (peerPingInterval < 0L)
				throw new IllegalArgumentException("peerPingInterval must be non-negative");

			this.peerPingInterval = peerPingInterval;
			return this;
		}

		/**
		 * Sets the time interval, in milliseconds, between successive pings to a peer.
		 * Every time the peer does not answer, its points are reduced by {@link #peerPunishmentForUnreachable},
		 * until they reach zero and the peer is removed.  During a successful ping, its peers are collected
		 * if they are useful for the node (for instance, if the node has too few peers).
		 * 
		 * @param whisperingMemorySize the size
		 * @return this builder
		 */
		public Builder setWhisperingMemorySize(long whisperingMemorySize) {
			if (whisperingMemorySize < 0L)
				throw new IllegalArgumentException("whisperingMemorySize must be non-negative");

			this.whisperingMemorySize = whisperingMemorySize;
			return this;
		}

		/**
		 * Builds the configuration.
		 * 
		 * @return the configuration
		 */
		public Config build() {
			return new Config(this);
		}

		@Override
		protected Builder getThis() {
			return this;
		}
	}
}