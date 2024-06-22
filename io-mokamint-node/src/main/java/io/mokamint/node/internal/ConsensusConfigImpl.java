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

import com.moandjiezana.toml.Toml;

import io.hotmoka.annotations.Immutable;
import io.hotmoka.crypto.HashingAlgorithms;
import io.hotmoka.crypto.SignatureAlgorithms;
import io.hotmoka.crypto.api.HashingAlgorithm;
import io.hotmoka.crypto.api.SignatureAlgorithm;
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
	public final HashingAlgorithm hashingForDeadlines;

	/**
	 * The hashing algorithm used for computing the next generation signature
	 * and the new scoop number from the previous block. It defaults to {@code sha256}.
	 */
	public final HashingAlgorithm hashingForGenerations;

	/**
	 * The hashing algorithm used for the identifying the blocks of
	 * the Mokamint blockchain. It defaults to {@code sha256}.
	 */
	public final HashingAlgorithm hashingForBlocks;

	/**
	 * The hashing algorithm used for the identifying the transactions of
	 * the Mokamint blockchain. It defaults to {@code sha256}.
	 */
	public final HashingAlgorithm hashingForTransactions;

	/**
	 * The signature algorithm that nodes must use to sign the blocks.
	 */
	public final SignatureAlgorithm signatureForBlocks;

	/**
	 * The signature algorithm that miners must use to sign the deadlines.
	 */
	public final SignatureAlgorithm signatureForDeadlines;

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
	public final int targetBlockCreationTime;

	/**
	 * The maximal size of a block's transactions table, in bytes.
	 */
	public final int maxBlockSize;

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
		this.hashingForTransactions = builder.hashingForTransactions;
		this.signatureForBlocks = builder.signatureForBlocks;
		this.signatureForDeadlines = builder.signatureForDeadlines;
		this.initialAcceleration = builder.initialAcceleration;
		this.targetBlockCreationTime = builder.targetBlockCreationTime;
		this.maxBlockSize = builder.maxBlockSize;
	}

	@Override
	public boolean equals(Object other) {
		return other instanceof ConsensusConfigImpl<?,?> otherConfig && getClass() == other.getClass() &&
			chainId.equals(otherConfig.chainId) &&
			maxBlockSize == otherConfig.maxBlockSize &&
			targetBlockCreationTime == otherConfig.targetBlockCreationTime &&
			initialAcceleration == otherConfig.initialAcceleration &&
			hashingForDeadlines.equals(otherConfig.hashingForDeadlines) &&
			hashingForGenerations.equals(otherConfig.hashingForGenerations) &&
			hashingForBlocks.equals(otherConfig.hashingForBlocks) &&
			signatureForBlocks.equals(otherConfig.getSignatureForBlocks()) &&
			signatureForDeadlines.equals(otherConfig.getSignatureForDeadlines());
	}

	@Override
	public int hashCode() {
		return chainId.hashCode() ^ Long.hashCode(maxBlockSize) ^ Long.hashCode(targetBlockCreationTime)
				^ Long.hashCode(initialAcceleration);
	}

	@Override
	public String toString() {
		return toToml();
	}

	@Override
	public String toToml() {
		var sb = new StringBuilder();

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
		sb.append("hashing_for_deadlines = \"" + hashingForDeadlines + "\"\n");
		sb.append("\n");
		sb.append("# the hashing algorithm used for the computation of the new generation and scoop number from the previous block\n");
		sb.append("hashing_for_generations = \"" + hashingForGenerations + "\"\n");
		sb.append("\n");
		sb.append("# the hashing algorithm used for the blocks of the blockchain\n");
		sb.append("hashing_for_blocks = \"" + hashingForBlocks + "\"\n");
		sb.append("\n");
		sb.append("# the hashing algorithm used for the transactions of the blockchain\n");
		sb.append("hashing_for_transactions = \"" + hashingForTransactions + "\"\n");
		sb.append("\n");
		sb.append("# the signature algorithm that nodes use to sign the blocks\n");
		sb.append("signature_for_blocks = \"" + signatureForBlocks + "\"\n");
		sb.append("\n");
		sb.append("# the signature algorithm that miners use to sign the deadlines\n");
		sb.append("signature_for_deadlines = \"" + signatureForDeadlines + "\"\n");
		sb.append("\n");
		sb.append("# the initial acceleration of the blockchain, at the genesis block;\n");
		sb.append("# this might be increased if the network starts with very little mining power\n");
		sb.append("initial_acceleration = " + initialAcceleration + "\n");
		sb.append("\n");
		sb.append("# time, in milliseconds, aimed between the creation of a block and the creation of a next block\n");
		sb.append("target_block_creation_time = " + targetBlockCreationTime + "\n");
		sb.append("\n");
		sb.append("# the maximal size, in bytes, of a block's transactions table\n");
		sb.append("max_block_size = " + maxBlockSize + "\n");

		return sb.toString();
	}

	@Override
	public String getChainId() {
		return chainId;
	}

	@Override
	public HashingAlgorithm getHashingForDeadlines() {
		return hashingForDeadlines;
	}

	@Override
	public HashingAlgorithm getHashingForGenerations() {
		return hashingForGenerations;
	}

	@Override
	public HashingAlgorithm getHashingForBlocks() {
		return hashingForBlocks;
	}

	@Override
	public HashingAlgorithm getHashingForTransactions() {
		return hashingForTransactions;
	}

	@Override
	public SignatureAlgorithm getSignatureForBlocks() {
		return signatureForBlocks;
	}

	@Override
	public SignatureAlgorithm getSignatureForDeadlines() {
		return signatureForDeadlines;
	}

	@Override
	public int getTargetBlockCreationTime() {
		return targetBlockCreationTime;
	}

	@Override
	public long getInitialAcceleration() {
		return initialAcceleration;
	}

	@Override
	public int getMaxBlockSize() {
		return maxBlockSize;
	}

	/**
	 * The builder of a configuration object.
	 * 
	 * @param <T> the concrete type of the builder
	 */
	public abstract static class ConsensusConfigBuilderImpl<C extends ConsensusConfig<C,B>, B extends ConsensusConfigBuilder<C,B>> implements ConsensusConfigBuilder<C,B> {
		private String chainId = "";
		private HashingAlgorithm hashingForDeadlines;
		private HashingAlgorithm hashingForGenerations;
		private HashingAlgorithm hashingForBlocks;
		private HashingAlgorithm hashingForTransactions;
		private SignatureAlgorithm signatureForBlocks;
		private SignatureAlgorithm signatureForDeadlines;
		private long initialAcceleration = 100000000000L;
		private int targetBlockCreationTime = 4 * 60 * 1000; // 4 minutes
		private int maxBlockSize = 1_000_000; // 1 megabyte

		protected ConsensusConfigBuilderImpl() throws NoSuchAlgorithmException {
			setHashingForDeadlines(HashingAlgorithms.shabal256());
			setHashingForGenerations(HashingAlgorithms.sha256());
			setHashingForBlocks(HashingAlgorithms.sha256());
			setHashingForTransactions(HashingAlgorithms.sha256());
			setSignatureForBlocks(SignatureAlgorithms.ed25519());
			setSignatureForDeadlines(SignatureAlgorithms.ed25519());
		}

		/**
		 * Reads the properties of the given TOML file and sets them for
		 * the corresponding fields of this builder.
		 * 
		 * @param toml the file
		 * @throws NoSuchAlgorithmException if some hashing algorithm cannot be found
		 */
		protected ConsensusConfigBuilderImpl(Toml toml) throws NoSuchAlgorithmException {
			this();

			var chainId = toml.getString("chain_id");
			if (chainId != null)
				setChainId(chainId);

			var hashingForDeadlines = toml.getString("hashing_for_deadlines");
			if (hashingForDeadlines != null)
				setHashingForDeadlines(hashingForDeadlines);
		
			var hashingForGenerations = toml.getString("hashing_for_generations");
			if (hashingForGenerations != null)
				setHashingForGenerations(hashingForGenerations);
		
			var hashingForBlocks = toml.getString("hashing_for_blocks");
			if (hashingForBlocks != null)
				setHashingForBlocks(hashingForBlocks);

			var hashingForTransactions = toml.getString("hashing_for_transactions");
			if (hashingForTransactions != null)
				setHashingForTransactions(hashingForTransactions);

			var signatureForBlocks = toml.getString("signature_for_blocks");
			if (signatureForBlocks != null)
				setSignatureForBlocks(signatureForBlocks);

			var signatureForDeadlines = toml.getString("signature_for_deadlines");
			if (signatureForDeadlines != null)
				setSignatureForDeadlines(signatureForDeadlines);

			var initialAcceleration = toml.getLong("initial_acceleration");
			if (initialAcceleration != null)
				setInitialAcceleration(initialAcceleration);

			var targetBlockCreationTime = toml.getLong("target_block_creation_time");
			if (targetBlockCreationTime != null)
				setTargetBlockCreationTime(targetBlockCreationTime);

			var maxBlockSize = toml.getLong("max_block_size");
			if (maxBlockSize != null)
				setMaxBlockSize(maxBlockSize);
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
			this.hashingForTransactions = config.getHashingForTransactions();
			this.signatureForBlocks = config.getSignatureForBlocks();
			this.signatureForDeadlines = config.getSignatureForDeadlines();
			this.initialAcceleration = config.getInitialAcceleration();
			this.targetBlockCreationTime = config.getTargetBlockCreationTime();
			this.maxBlockSize = config.getMaxBlockSize();
		}

		@Override
		public B setChainId(String chainId) {
			Objects.requireNonNull(chainId, "chainId cannot be null");
			this.chainId = chainId;
			return getThis();
		}

		private void setHashingForDeadlines(String hashingForDeadlines) throws NoSuchAlgorithmException {
			this.hashingForDeadlines = HashingAlgorithms.of(hashingForDeadlines);
		}

		private void setHashingForGenerations(String hashingForGenerations) throws NoSuchAlgorithmException {
			this.hashingForGenerations = HashingAlgorithms.of(hashingForGenerations);
		}

		private void setHashingForBlocks(String hashingForBlocks) throws NoSuchAlgorithmException {
			this.hashingForBlocks = HashingAlgorithms.of(hashingForBlocks);
		}

		private void setHashingForTransactions(String hashingForTransactions) throws NoSuchAlgorithmException {
			this.hashingForTransactions = HashingAlgorithms.of(hashingForTransactions);
		}

		private void setSignatureForBlocks(String signatureForBlocks) throws NoSuchAlgorithmException {
			this.signatureForBlocks = SignatureAlgorithms.of(signatureForBlocks);
		}

		private void setSignatureForDeadlines(String signatureForDeadlines) throws NoSuchAlgorithmException {
			this.signatureForDeadlines = SignatureAlgorithms.of(signatureForDeadlines);
		}

		@Override
		public B setHashingForDeadlines(HashingAlgorithm hashingForDeadlines) {
			this.hashingForDeadlines = hashingForDeadlines;
			return getThis();
		}

		@Override
		public B setHashingForGenerations(HashingAlgorithm hashingForGenerations) {
			this.hashingForGenerations = hashingForGenerations;
			return getThis();
		}

		@Override
		public B setHashingForBlocks(HashingAlgorithm hashingForBlocks) {
			this.hashingForBlocks = hashingForBlocks;
			return getThis();
		}

		@Override
		public B setHashingForTransactions(HashingAlgorithm hashingForTransactions) {
			this.hashingForTransactions = hashingForTransactions;
			return getThis();
		}

		@Override
		public B setSignatureForBlocks(SignatureAlgorithm signatureForBlocks) {
			this.signatureForBlocks = signatureForBlocks;
			return getThis();
		}

		@Override
		public B setSignatureForDeadlines(SignatureAlgorithm signatureForDeadlines) {
			this.signatureForDeadlines = signatureForDeadlines;
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
		public B setTargetBlockCreationTime(int targetBlockCreationTime) {
			if (targetBlockCreationTime < 1)
				throw new IllegalArgumentException("targetBlockCreationTime must be positive");

			this.targetBlockCreationTime = targetBlockCreationTime;
			return getThis();
		}

		private B setTargetBlockCreationTime(long targetBlockCreationTime) {
			if (targetBlockCreationTime < 1 || targetBlockCreationTime > Integer.MAX_VALUE)
				throw new IllegalArgumentException("targetBlockCreationTime must be between 1 and " + Integer.MAX_VALUE + " inclusive");

			this.targetBlockCreationTime = (int) targetBlockCreationTime;
			return getThis();
		}

		@Override
		public B setMaxBlockSize(int maxBlockSize) {
			if (maxBlockSize < 0)
				throw new IllegalArgumentException("maxBlockSize cannot be negative");

			this.maxBlockSize = maxBlockSize;
			return getThis();
		}

		private B setMaxBlockSize(long maxBlockSize) {
			if (maxBlockSize < 0 || maxBlockSize > Integer.MAX_VALUE)
				throw new IllegalArgumentException("maxBlockSize must be between 0 and " + Integer.MAX_VALUE + " inclusive");

			this.maxBlockSize = (int) maxBlockSize;
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