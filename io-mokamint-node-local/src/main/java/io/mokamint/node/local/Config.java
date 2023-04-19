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

import io.hotmoka.annotations.Immutable;
import io.hotmoka.toml.Toml;

/**
 * The configuration of a local Mokamint node.
 */
@Immutable
public class Config {

	/**
	 * The path where the node's data will be persisted.
	 * It defaults to {@code chain} in the current directory.
	 */
	public final Path dir;

	/**
	 * The target time interval, in milliseconds, between the creation of a block
	 * and the creation of a next block. The network will strive to get close
	 * to this time. The higher the hashing power of the network, the more precise
	 * this will be.
	 */
	public final long targetBlockCreationTime;

	/**
	 * The maximal delay, in milliseconds, between a deadline request to the miners
	 * and the reception of the first deadline from the miners.
	 */
	public final long deadlineWaitTimeout;

	/**
	 * The initial points of a miner, freshly connected to a node.
	 */
	public final long minerInitialPoints;

	/**
	 * The points lost for punishment by a miner that timeouts
	 * at a request for a deadline.
	 */
	public final long minerPunishmentForTimeout;

	/**
	 * The points lost by a miner that provides an illegal deadline.
	 */
	public final long minerPunishmentForIllegalDeadline;

	/**
	 * Full constructor for the builder pattern.
	 */
	private Config(Path dir, long targetBlockCreationTime, long deadlineWaitTimeout, long minerInitialPoints, long minerPunishmentForTimeout, long minerPunishmentForIllegalDeadline) {
		this.dir = dir;
		this.targetBlockCreationTime = targetBlockCreationTime;
		this.deadlineWaitTimeout = deadlineWaitTimeout;
		this.minerInitialPoints = minerInitialPoints;
		this.minerPunishmentForTimeout = minerPunishmentForTimeout;
		this.minerPunishmentForIllegalDeadline = minerPunishmentForIllegalDeadline;
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
		sb.append("# time, in milliseconds, aimed between the creation of a block and the creation of a next block\n");
		sb.append("target_block_creation_time = " + targetBlockCreationTime + "\n");
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
		private Path dir = Paths.get("chain");
		private long targetBlockCreationTime = 4 * 60 * 1000L; // 4 minutes
		private long deadlineWaitTimeout = 20000L;
		private long minerInitialPoints = 1000L;
		private long minerPunishmentForTimeout = 1L;
		private long minerPunishmentForIllegalDeadline = 500L;

		private Builder() {}

		/**
		 * Creates a builder containing default data.
		 * 
		 * @return the builder
		 */
		public static Builder defaults() {
			return new Builder();
		}

		/**
		 * Creates a builder from the given TOML configuration file.
		 * The resulting builder will contain the information in the file,
		 * and use defaults for the data not contained in the file.
		 * 
		 * @param path the path to the TOML file
		 * @return the builder
		 */
		public static Builder load(Path path) {
			var toml = new Toml().read(path.toFile());
			var builder = new Builder();

			var dir = toml.getString("dir");
			if (dir != null)
				builder.setDir(Paths.get(dir));

			var targetBlockCreationTime = toml.getLong("target_block_creation_time");
			if (targetBlockCreationTime != null)
				builder.setTargetBlockCreationTime(targetBlockCreationTime);

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
		 * It defaults to {@code chain} in the current directory.
		 * 
		 * @param dir the directory
		 * @return this builder
		 */
		public Builder setDir(Path dir) {
			this.dir = dir;
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
		 * Sets the maximal delay, in milliseconds, between the deadline request to the miners
		 * and the reception of the first deadline from the miners.
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
			return new Config(dir, targetBlockCreationTime, deadlineWaitTimeout, minerInitialPoints, minerPunishmentForTimeout, minerPunishmentForIllegalDeadline);
		}
	}
}