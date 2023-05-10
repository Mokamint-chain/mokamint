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

import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.NoSuchAlgorithmException;

import com.moandjiezana.toml.Toml;

import io.hotmoka.annotations.Immutable;
import io.hotmoka.crypto.HashingAlgorithms;
import io.hotmoka.crypto.api.HashingAlgorithm;

/**
 * The configuration of a local Mokamint node.
 */
@Immutable
public class Config {

	/**
	 * The path where the node's data will be persisted.
	 * It defaults to {@code mokamint-chain} in the current directory.
	 */
	public final Path dir;

	/**
	 * The hashing algorithm used for computing the deadlines, hence
	 * also in the plot files used by the miners. It defaults to <code>shabal256</code>.
	 */
	public final HashingAlgorithm<byte[]> hashingForDeadlines;

	/**
	 * The hashing algorithm used for computing the next generation
	 * signature and the new scoop number from the previous block. It defaults to
	 * <code>sha256</code>.
	 */
	public final HashingAlgorithm<byte[]> hashingForGenerations;

	/**
	 * The hashing algorithm used for the identifying the blocks of
	 * the Mokamint blockchain. It defaults to <code>sha256</code>.
	 */
	public final HashingAlgorithm<byte[]> hashingForBlocks;

	/**
	 * The target time interval, in milliseconds, between the creation of a block
	 * and the creation of a next block. The network will strive to get close
	 * to this time. The higher the hashing power of the network, the more precise
	 * this will be.
	 */
	public final long targetBlockCreationTime;

	/**
	 * The time, in milliseconds, that a miner can use at most to accept a request for a deadline.
	 * After this threshold, a time-out occurs and the miner will get punished.
	 * Note that the deadline might arrive well beyond this threshold (or maybe never).
	 * It defaults to 10000.
	 */
	public final long minerRequestTimeout;

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
	 * Full constructor for the builder pattern.
	 */
	private Config(Builder builder) {
		this.dir = builder.dir;
		this.hashingForDeadlines = builder.hashingForDeadlines;
		this.hashingForGenerations = builder.hashingForGenerations;
		this.hashingForBlocks = builder.hashingForBlocks;
		this.targetBlockCreationTime = builder.targetBlockCreationTime;
		this.minerRequestTimeout = builder.minerRequestTimeout;
		this.deadlineWaitTimeout = builder.deadlineWaitTimeout;
		this.minerInitialPoints = builder.minerInitialPoints;
		this.minerPunishmentForTimeout = builder.minerPunishmentForTimeout;
		this.minerPunishmentForIllegalDeadline = builder.minerPunishmentForIllegalDeadline;
	}

	@Override
	public String toString() {
		return toToml();
	}

	/**
	 * Yields a toml representation of this configuration. In the future, it can be loaded
	 * through {@link Builder#load(Path)}.
	 * 
	 * @return the toml representation, as a string
	 */
	public String toToml() {
		StringBuilder sb = new StringBuilder();
		sb.append("# This is a TOML config file for Mokamint nodes.\n");
		sb.append("# For more information about TOML, see https://github.com/toml-lang/toml\n");
		sb.append("# For more information about Mokamint, see https://www.mokamint.io\n");
		sb.append("\n");
		sb.append("# NOTE: Any path below can be absolute or relative to the directory\n");
		sb.append("# where the mokamint-node command is run.\n");
		sb.append("\n");
		sb.append("## Main options\n");
		sb.append("\n");
		sb.append("# the path where the node's data will be persisted\n");
		sb.append("dir = \"" + dir + "\"\n");
		sb.append("\n");
		sb.append("# the hashing algorithm used for the deadlines and hence for the plot files of the miners\n");
		sb.append("hashing_for_deadlines = \"" + hashingForDeadlines.getName() + "\"\n");
		sb.append("\n");
		sb.append("# the hashing algorithm used for the computation of the new generation and scoop number from the previous block\n");
		sb.append("hashing_for_generations = \"" + hashingForGenerations.getName() + "\"\n");
		sb.append("\n");
		sb.append("# the hashing algorithm used for the blocks of the blockchain\n");
		sb.append("hashing_for_blocks = \"" + hashingForBlocks.getName() + "\"\n");
		sb.append("\n");
		sb.append("# time, in milliseconds, aimed between the creation of a block and the creation of a next block\n");
		sb.append("target_block_creation_time = " + targetBlockCreationTime + "\n");
		sb.append("\n");
		sb.append("# maximal milliseconds to wait for a deadline request to be accepted by a miner\n");
		sb.append("miner_request_timeout = " + minerRequestTimeout + "\n");
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

		return sb.toString();
	}

	/**
	 * The builder of a configuration object.
	 */
	public static class Builder {
		private Path dir = Paths.get("mokamint-chain");
		private HashingAlgorithm<byte[]> hashingForDeadlines;
		private HashingAlgorithm<byte[]> hashingForGenerations;
		private HashingAlgorithm<byte[]> hashingForBlocks;
		private long targetBlockCreationTime = 4 * 60 * 1000L; // 4 minutes
		private long minerRequestTimeout = 10000L;
		private long deadlineWaitTimeout = 20000L;
		private long minerInitialPoints = 1000L;
		private long minerPunishmentForTimeout = 1L;
		private long minerPunishmentForIllegalDeadline = 500L;

		private Builder() throws NoSuchAlgorithmException {
			setHashingForDeadlines("shabal256");
			setHashingForGenerations("sha256");
			setHashingForBlocks("sha256");
		}

		/**
		 * Creates a builder containing default data.
		 * 
		 * @return the builder
		 * @throws NoSuchAlgorithmException 
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
		 * @throws NoSuchAlgorithmException 
		 */
		public static Builder load(Path path) throws NoSuchAlgorithmException {
			var toml = new Toml().read(path.toFile());
			var builder = new Builder();

			var dir = toml.getString("dir");
			if (dir != null)
				builder.setDir(Paths.get(dir));

			var hashingForDeadlines = toml.getString("hashing_for_deadlines");
			if (hashingForDeadlines != null)
				builder.setHashingForDeadlines(hashingForDeadlines);

			var hashingForGenerations = toml.getString("hashing_for_generations");
			if (hashingForGenerations != null)
				builder.setHashingForGenerations(hashingForGenerations);

			var hashingForBlocks = toml.getString("hashing_for_blocks");
			if (hashingForBlocks != null)
				builder.setHashingForBlocks(hashingForBlocks);

			var targetBlockCreationTime = toml.getLong("target_block_creation_time");
			if (targetBlockCreationTime != null)
				builder.setTargetBlockCreationTime(targetBlockCreationTime);

			var minerRequestTimeout = toml.getLong("miner_request_timeout");
			if (minerRequestTimeout != null)
				builder.setMinerRequestTimeout(minerRequestTimeout);

			var deadlineWaitTimeout = toml.getLong("deadline_wait_timeout");
			if (deadlineWaitTimeout != null)
				builder.setDeadlineWaitTimeout(deadlineWaitTimeout);

			var minerInitialPoints = toml.getLong("miner_initial_points");
			if (minerInitialPoints != null)
				builder.setMinerInitialPoints(minerInitialPoints);

			var minerPunishmentForTimeout = toml.getLong("miner_punishment_for_timeout");
			if (minerPunishmentForTimeout != null)
				builder.setMinerPunishmentForTimeout(minerPunishmentForTimeout);

			var minerPunishmentForIllegalDeadline = toml.getLong("miner_punishment_for_illegal_deadline");
			if (minerPunishmentForIllegalDeadline != null)
				builder.setMinerPunishmentForIllegalDeadline(minerPunishmentForIllegalDeadline);

			return builder;
		}

		/**
		 * Sets the directory where the node's data will be persisted.
		 * It defaults to {@code mokamint-chain} in the current directory.
		 * 
		 * @param dir the directory
		 * @return this builder
		 */
		public Builder setDir(Path dir) {
			this.dir = dir;
			return this;
		}

		/**
		 * Sets the hashing algorithm for computing the deadlines and hence also the
		 * plot files used by the miners.
		 * 
		 * @param hashingForDeadlines the name of the hashing algorithm
		 * @return this builder
		 * @throws NoSuchAlgorithmException if no algorithm exists with that name
		 */
		public Builder setHashingForDeadlines(String hashingForDeadlines) throws NoSuchAlgorithmException {
			this.hashingForDeadlines = HashingAlgorithms.mk(hashingForDeadlines, (byte[] bytes) -> bytes);
			return this;
		}

		/**
		 * Sets the hashing algorithm for computing the new generation and the new scoop
		 * number from the previous block.
		 * 
		 * @param hashingForGenerations the name of the hashing algorithm
		 * @return this builder
		 * @throws NoSuchAlgorithmException if no algorithm exists with that name
		 */
		public Builder setHashingForGenerations(String hashingForGenerations) throws NoSuchAlgorithmException {
			this.hashingForGenerations = HashingAlgorithms.mk(hashingForGenerations, (byte[] bytes) -> bytes);
			return this;
		}

		/**
		 * Sets the hashing algorithm for identifying the blocks in the Mokamint blockchain.
		 * 
		 * @param hashingForGenerations the name of the hashing algorithm
		 * @return this builder
		 * @throws NoSuchAlgorithmException if no algorithm exists with that name
		 */
		public Builder setHashingForBlocks(String hashingForBlocks) throws NoSuchAlgorithmException {
			this.hashingForBlocks = HashingAlgorithms.mk(hashingForBlocks, (byte[] bytes) -> bytes);
			return this;
		}

		/**
		 * Sets the target time interval, in milliseconds, between the creation of a block
		 * and the creation of a next block.  The network will strive to get close
		 * to this time. The higher the hashing power of the network, the more precise
		 * this will be.
		 * 
		 * @param targetBlockCreationTime the target time interval, in milliseconds
		 * @return this builder
		 */
		public Builder setTargetBlockCreationTime(long targetBlockCreationTime) {
			this.targetBlockCreationTime = targetBlockCreationTime;
			return this;
		}

		/**
		 * Sets the time, in milliseconds, that a miner can use at most to accept a request for a deadline.
		 * After this threshold, a time-out occurs and the miner will get punished.
		 * Note that the deadline might arrive well beyond this threshold (or maybe never).
		 * It defaults to 10000.
		 * 
		 * @param minerRequestTimeout the wait time, in milliseconds
		 * @return this builder
		 */
		public Builder setMinerRequestTimeout(long minerRequestTimeout) {
			this.minerRequestTimeout = minerRequestTimeout;
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
			this.minerPunishmentForTimeout = minerPunishmentForTimeout;
			return this;
		}

		/**
		 * Sets the points lost by a miner, as punishment for providing an illegal deadline.
		 * This defaults to 500.
		 * 
		 * @param minerPunishmentForTimeout the points
		 * @return this builder
		 */
		public Builder setMinerPunishmentForIllegalDeadline(long minerPunishmentForIllegalDeadline) {
			this.minerPunishmentForIllegalDeadline = minerPunishmentForIllegalDeadline;
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
	}
}