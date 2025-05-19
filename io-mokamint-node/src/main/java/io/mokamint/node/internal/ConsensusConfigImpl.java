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
import io.hotmoka.websockets.beans.api.InconsistentJsonException;
import io.mokamint.node.api.ConsensusConfig;
import io.mokamint.node.api.ConsensusConfigBuilder;
import io.mokamint.node.internal.gson.BasicConsensusConfigJson;

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
	 * The rapidity of changes of the acceleration for block creation.
	 * It is a value between 0 (no acceleration change) to 100,000 (maximally fast change).
	 */
	public final int oblivion;

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
		this.targetBlockCreationTime = builder.targetBlockCreationTime;
		this.maxBlockSize = builder.maxBlockSize;
		this.oblivion = builder.oblivion;
	}

	@Override
	public boolean equals(Object other) {
		return other instanceof ConsensusConfigImpl<?,?> otherConfig && getClass() == other.getClass() &&
			chainId.equals(otherConfig.chainId) &&
			maxBlockSize == otherConfig.maxBlockSize &&
			oblivion == otherConfig.oblivion &&
			targetBlockCreationTime == otherConfig.targetBlockCreationTime &&
			hashingForDeadlines.equals(otherConfig.hashingForDeadlines) &&
			hashingForGenerations.equals(otherConfig.hashingForGenerations) &&
			hashingForBlocks.equals(otherConfig.hashingForBlocks) &&
			signatureForBlocks.equals(otherConfig.getSignatureForBlocks()) &&
			signatureForDeadlines.equals(otherConfig.getSignatureForDeadlines());
	}

	@Override
	public int hashCode() {
		return chainId.hashCode() ^ Long.hashCode(maxBlockSize) ^ Long.hashCode(targetBlockCreationTime) ^ oblivion;
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
		sb.append("# time, in milliseconds, aimed between the creation of a block and the creation of a next block\n");
		sb.append("target_block_creation_time = " + targetBlockCreationTime + "\n");
		sb.append("\n");
		sb.append("# the maximal size, in bytes, of a block's transactions table\n");
		sb.append("max_block_size = " + maxBlockSize + "\n");
		sb.append("\n");
		sb.append("# the rapidity of changes of the acceleration for the block creation time\n");
		sb.append("# between 0 (no change) and 100,000 (maximally fast change)\n");
		sb.append("oblivion = " + oblivion + "\n");

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
	public int getMaxBlockSize() {
		return maxBlockSize;
	}

	@Override
	public int getOblivion() {
		return oblivion;
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
		private int targetBlockCreationTime = 4 * 60 * 1000; // 4 minutes
		private int maxBlockSize = 1_000_000; // 1 megabyte
		private int oblivion = 20_000;

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

			var targetBlockCreationTime = toml.getLong("target_block_creation_time");
			if (targetBlockCreationTime != null)
				setTargetBlockCreationTime(targetBlockCreationTime);

			var maxBlockSize = toml.getLong("max_block_size");
			if (maxBlockSize != null)
				setMaxBlockSize(maxBlockSize);

			var oblivion = toml.getLong("oblivion");
			if (oblivion != null)
				setOblivion(oblivion);
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
			this.targetBlockCreationTime = config.getTargetBlockCreationTime();
			this.maxBlockSize = config.getMaxBlockSize();
			this.oblivion = config.getOblivion();
		}

		/**
		 * Creates a consensus builder from the given JSON representation.
		 * 
		 * @param json the JSON representation
		 * @throws InconsistentJsonException if {@code json} is inconsistent
		 * @throws NoSuchAlgorithmException if {@code json} refers to a non-available cryptographic algorithm
		 */
		protected ConsensusConfigBuilderImpl(BasicConsensusConfigJson json) throws InconsistentJsonException, NoSuchAlgorithmException {
			Objects.requireNonNull(json);

			try {
				setChainId(json.getChainId());
				setHashingForDeadlines(json.getHashingForDeadlines());
				setHashingForGenerations(json.getHashingForGenerations());
				setHashingForBlocks(json.getHashingForBlocks());
				setHashingForTransactions(json.getHashingForTransactions());
				setSignatureForBlocks(json.getSignatureForBlocks());
				setSignatureForDeadlines(json.getSignatureForDeadlines());
				setTargetBlockCreationTime(json.getTargetBlockCreationTime());
				setMaxBlockSize(json.getMaxBlockSize());
				setOblivion(json.getOblivion());
			}
			catch (NullPointerException | IllegalArgumentException e) {
				throw new InconsistentJsonException(e.getMessage());
			}
		}

		@Override
		public B setChainId(String chainId) {
			this.chainId = Objects.requireNonNull(chainId, "chainId cannot be null");
			return getThis();
		}

		private void setHashingForDeadlines(String hashingForDeadlines) throws NoSuchAlgorithmException {
			this.hashingForDeadlines = HashingAlgorithms.of(Objects.requireNonNull(hashingForDeadlines, "hashingForDeadlines cannot be null"));
		}

		private void setHashingForGenerations(String hashingForGenerations) throws NoSuchAlgorithmException {
			this.hashingForGenerations = HashingAlgorithms.of(Objects.requireNonNull(hashingForGenerations, "hashingForGenerations cannot be null"));
		}

		private void setHashingForBlocks(String hashingForBlocks) throws NoSuchAlgorithmException {
			this.hashingForBlocks = HashingAlgorithms.of(Objects.requireNonNull(hashingForBlocks, "hashingForBlocks cannot be null"));
		}

		private void setHashingForTransactions(String hashingForTransactions) throws NoSuchAlgorithmException {
			this.hashingForTransactions = HashingAlgorithms.of(Objects.requireNonNull(hashingForTransactions, "hashingForTransactions cannot be null"));
		}

		private void setSignatureForBlocks(String signatureForBlocks) throws NoSuchAlgorithmException {
			this.signatureForBlocks = SignatureAlgorithms.of(Objects.requireNonNull(signatureForBlocks, "signatureForBlocks cannot be null"));
		}

		private void setSignatureForDeadlines(String signatureForDeadlines) throws NoSuchAlgorithmException {
			this.signatureForDeadlines = SignatureAlgorithms.of(Objects.requireNonNull(signatureForDeadlines, "signatureForDeadlines cannot be null"));
		}

		@Override
		public B setHashingForDeadlines(HashingAlgorithm hashingForDeadlines) {
			this.hashingForDeadlines = Objects.requireNonNull(hashingForDeadlines, "hashingForDeadlines cannot be null");
			return getThis();
		}

		@Override
		public B setHashingForGenerations(HashingAlgorithm hashingForGenerations) {
			this.hashingForGenerations = Objects.requireNonNull(hashingForGenerations, "hashingForGenerations cannot be null");
			return getThis();
		}

		@Override
		public B setHashingForBlocks(HashingAlgorithm hashingForBlocks) {
			this.hashingForBlocks = Objects.requireNonNull(hashingForBlocks, "hashingForBlocks cannot be null");
			return getThis();
		}

		@Override
		public B setHashingForTransactions(HashingAlgorithm hashingForTransactions) {
			this.hashingForTransactions = Objects.requireNonNull(hashingForTransactions, "hashingForTransactions cannot be null");
			return getThis();
		}

		@Override
		public B setSignatureForBlocks(SignatureAlgorithm signatureForBlocks) {
			this.signatureForBlocks = Objects.requireNonNull(signatureForBlocks, "signatureForBlocks cannot be null");
			return getThis();
		}

		@Override
		public B setSignatureForDeadlines(SignatureAlgorithm signatureForDeadlines) {
			this.signatureForDeadlines = Objects.requireNonNull(signatureForDeadlines, "signatureForDeadlines cannot be null");
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

		@Override
		public B setOblivion(int oblivion) {
			return setOblivion((long) oblivion);
		}

		private B setOblivion(long oblivion) {
			if (oblivion < 0 || oblivion > 100_000)
				throw new IllegalArgumentException("oblivion must be between 0 and 100,000 (inclusive)");

			this.oblivion = (int) oblivion;
			return getThis();
		}

		/**
		 * Standard design pattern. See http://www.angelikalanger.com/GenericsFAQ/FAQSections/ProgrammingIdioms.html#FAQ205
		 * 
		 * @return this same builder
		 */
		protected abstract B getThis();

		/**
		 * Yields the target block creation time.
		 * 
		 * @return the target block creation time, in milliseconds
		 */
		protected int getTargetBlockCreationTime() {
			return targetBlockCreationTime;
		}

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