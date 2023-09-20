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

package io.mokamint.node.internal;

import java.io.FileNotFoundException;
import java.nio.file.Path;
import java.security.NoSuchAlgorithmException;
import java.util.Objects;
import java.util.function.Function;

import com.moandjiezana.toml.Toml;

import io.hotmoka.annotations.Immutable;
import io.hotmoka.crypto.HashingAlgorithms;
import io.hotmoka.crypto.api.HashingAlgorithm;
import io.mokamint.node.api.ConsensusConfig;
import io.mokamint.node.api.ConsensusConfigBuilder;

/**
 * The configuration of a Mokamint node. Nodes of the same network must agree
 * on this data in order to achieve consensus.
 * 
 * @param <C> the concrete type of the configuration
 * @param <B> the concrete type of the builder
 */
@Immutable
public abstract class ConsensusConfigImpl<C extends ConsensusConfig<C,B>, B extends ConsensusConfigBuilder<C,B>> implements ConsensusConfig<C,B> {

	/**
	 * The chain identifier of the blockchain the node belongs to.
	 * It defaults to the empty string.
	 */
	public final String chainId;

	/**
	 * The hashing algorithm used for computing the deadlines, hence
	 * also in the plot files used by the miners. It defaults to {@code shabal256}.
	 */
	public final HashingAlgorithm<byte[]> hashingForDeadlines;

	/**
	 * The hashing algorithm used for computing the next generation signature
	 * and the new scoop number from the previous block. It defaults to {@code sha256}.
	 */
	public final HashingAlgorithm<byte[]> hashingForGenerations;

	/**
	 * The hashing algorithm used for the identifying the blocks of
	 * the Mokamint blockchain. It defaults to {@code sha256}.
	 */
	public final HashingAlgorithm<byte[]> hashingForBlocks;

	/**
	 * The acceleration for the genesis block. This specifies how
	 * quickly get blocks generated at the beginning of a chain. The less
	 * mining power has the network at the beginning, the higher the
	 * initial acceleration should be, or otherwise the creation of the first blocks
	 * might take a long time. It defaults to 100000000000.
	 */
	public final long initialAcceleration;

	/**
	 * The target time interval, in milliseconds, between the creation of a block
	 * and the creation of a next block. The network will strive to get close
	 * to this time. The higher the hashing power of the network, the more precise
	 * this will be.
	 */
	public final long targetBlockCreationTime;

	/**
	 * Full constructor for the builder pattern.
	 * 
	 * @param builder the builder where information is extracted from
	 */
	protected ConsensusConfigImpl(ConsensusConfigBuilderImpl<C,B> builder) {
		this.chainId = builder.chainId;
		this.hashingForDeadlines = builder.hashingForDeadlines;
		this.hashingForGenerations = builder.hashingForGenerations;
		this.hashingForBlocks = builder.hashingForBlocks;
		this.initialAcceleration = builder.initialAcceleration;
		this.targetBlockCreationTime = builder.targetBlockCreationTime;
	}

	@Override
	public boolean equals(Object other) {
		if (other != null && getClass() == other.getClass()) {
			var otherConfig = (ConsensusConfigImpl<?,?>) other;
			return chainId.equals(otherConfig.chainId) &&
				targetBlockCreationTime == otherConfig.targetBlockCreationTime &&
				initialAcceleration == otherConfig.initialAcceleration &&
				hashingForDeadlines.getName().equals(otherConfig.hashingForDeadlines.getName()) &&
				hashingForGenerations.getName().equals(otherConfig.hashingForGenerations.getName()) &&
				hashingForBlocks.getName().equals(otherConfig.hashingForBlocks.getName());
		}
		else
			return false;
	}

	@Override
	public String toString() {
		return toToml();
	}

	@Override
	public String toToml() {
		StringBuilder sb = new StringBuilder();
		sb.append("# This is a TOML config file for Mokamint nodes.\n");
		sb.append("# For more information about TOML, see https://github.com/toml-lang/toml\n");
		sb.append("# For more information about Mokamint, see https://www.mokamint.io\n");
		sb.append("\n");
		sb.append("## Consensus parameters\n");
		sb.append("\n");
		sb.append("# the chain identifier of the blockchain\n");
		sb.append("chain_id = \"" + chainId + "\"\n");
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
		sb.append("# the initial acceleration of the blockchain, at the genesis block;\n");
		sb.append("# this might be increased if the network starts with very little mining power\n");
		sb.append("initial_acceleration = " + initialAcceleration + "\n");
		sb.append("\n");
		sb.append("# time, in milliseconds, aimed between the creation of a block and the creation of a next block\n");
		sb.append("target_block_creation_time = " + targetBlockCreationTime + "\n");

		return sb.toString();
	}

	@Override
	public String getChainId() {
		return chainId;
	}

	@Override
	public HashingAlgorithm<byte[]> getHashingForDeadlines() {
		return hashingForDeadlines;
	}

	@Override
	public HashingAlgorithm<byte[]> getHashingForGenerations() {
		return hashingForGenerations;
	}

	@Override
	public HashingAlgorithm<byte[]> getHashingForBlocks() {
		return hashingForBlocks;
	}

	@Override
	public long getTargetBlockCreationTime() {
		return targetBlockCreationTime;
	}

	@Override
	public long getInitialAcceleration() {
		return initialAcceleration;
	}

	/**
	 * The builder of a configuration object.
	 * 
	 * @param <T> the concrete type of the builder
	 */
	public abstract static class ConsensusConfigBuilderImpl<C extends ConsensusConfig<C,B>, B extends ConsensusConfigBuilder<C,B>> implements ConsensusConfigBuilder<C,B> {
		private String chainId = "";
		private HashingAlgorithm<byte[]> hashingForDeadlines;
		private HashingAlgorithm<byte[]> hashingForGenerations;
		private HashingAlgorithm<byte[]> hashingForBlocks;
		private long initialAcceleration = 100000000000L;
		private long targetBlockCreationTime = 4 * 60 * 1000L; // 4 minutes

		protected ConsensusConfigBuilderImpl() throws NoSuchAlgorithmException {
			setHashingForDeadlines(HashingAlgorithms.TYPES.SHABAL256.name());
			setHashingForGenerations(HashingAlgorithms.TYPES.SHA256.name());
			setHashingForBlocks(HashingAlgorithms.TYPES.SHA256.name());
		}

		/**
		 * Reads the properties of the given TOML file and sets them for
		 * the corresponding fields of this builder.
		 * 
		 * @param toml the file
		 * @throws NoSuchAlgorithmException if some hashing algorithm cannot be found
		 */
		protected ConsensusConfigBuilderImpl(Toml toml) throws NoSuchAlgorithmException {
			var chainId = toml.getString("chain_id");
			if (chainId != null)
				setChainId(chainId);

			var hashingForDeadlines = toml.getString("hashing_for_deadlines");
			if (hashingForDeadlines != null)
				setHashingForDeadlines(hashingForDeadlines);
			else
				setHashingForDeadlines(HashingAlgorithms.TYPES.SHABAL256.name());
		
			var hashingForGenerations = toml.getString("hashing_for_generations");
			if (hashingForGenerations != null)
				setHashingForGenerations(hashingForGenerations);
			else
				setHashingForGenerations(HashingAlgorithms.TYPES.SHA256.name());
		
			var hashingForBlocks = toml.getString("hashing_for_blocks");
			if (hashingForBlocks != null)
				setHashingForBlocks(hashingForBlocks);
			else
				setHashingForBlocks(HashingAlgorithms.TYPES.SHA256.name());

			var initialAcceleration = toml.getLong("initial_acceleration");
			if (initialAcceleration != null)
				setInitialAcceleration(initialAcceleration);

			var targetBlockCreationTime = toml.getLong("target_block_creation_time");
			if (targetBlockCreationTime != null)
				setTargetBlockCreationTime(targetBlockCreationTime);
		}

		/**
		 * Creates a builder with properties initialized to those of the given configuration object.
		 * 
		 * @param config the configuration object
		 */
		protected ConsensusConfigBuilderImpl(ConsensusConfig<C,B> config) {
			this.chainId = config.getChainId();
			this.hashingForDeadlines = config.getHashingForDeadlines();
			this.hashingForGenerations = config.getHashingForGenerations();
			this.hashingForBlocks = config.getHashingForBlocks();
			this.initialAcceleration = config.getInitialAcceleration();
			this.targetBlockCreationTime = config.getTargetBlockCreationTime();
		}

		@Override
		public B setChainId(String chainId) {
			Objects.requireNonNull(chainId, "chainId cannot be null");
			this.chainId = chainId;
			return getThis();
		}

		@Override
		public B setHashingForDeadlines(String hashingForDeadlines) throws NoSuchAlgorithmException {
			Objects.requireNonNull(hashingForDeadlines, "hashingForDeadlines cannot be null");
			this.hashingForDeadlines = HashingAlgorithms.of(hashingForDeadlines, Function.identity());
			return getThis();
		}

		@Override
		public B setHashingForGenerations(String hashingForGenerations) throws NoSuchAlgorithmException {
			Objects.requireNonNull(hashingForGenerations, "hashingForGenerations cannot be null");
			this.hashingForGenerations = HashingAlgorithms.of(hashingForGenerations, Function.identity());
			return getThis();
		}

		@Override
		public B setHashingForBlocks(String hashingForBlocks) throws NoSuchAlgorithmException {
			Objects.requireNonNull(hashingForBlocks, "hashingForBlocks cannot be null");
			this.hashingForBlocks = HashingAlgorithms.of(hashingForBlocks, Function.identity());
			return getThis();
		}

		@Override
		public B setInitialAcceleration(long initialAcceleration) {
			if (initialAcceleration < 1L)
				throw new IllegalArgumentException("initialAcceleration must be positive");

			this.initialAcceleration = initialAcceleration;
			return getThis();
		}

		@Override
		public B setTargetBlockCreationTime(long targetBlockCreationTime) {
			if (targetBlockCreationTime < 1L)
				throw new IllegalArgumentException("targetBlockCreationTime must be positive");

			this.targetBlockCreationTime = targetBlockCreationTime;
			return getThis();
		}

		/**
		 * Standard design pattern. See http://www.angelikalanger.com/GenericsFAQ/FAQSections/ProgrammingIdioms.html#FAQ205
		 * 
		 * @return this same builder
		 */
		protected abstract B getThis();

		/**
		 * Loads the TOML file at the given path.
		 * 
		 * @param path the path
		 * @return the file
		 * @throws FileNotFoundException if {@code path} cannot be found
		 */
		protected static Toml readToml(Path path) throws FileNotFoundException {
			try {
				return new Toml().read(path.toFile());
			}
			catch (RuntimeException e) {
				// the toml4j library wraps the FileNotFoundException inside a RuntimeException...
				Throwable cause = e.getCause();
				if (cause instanceof FileNotFoundException fnfe)
					throw fnfe;
				else
					throw e;
			}
		}
	}
}