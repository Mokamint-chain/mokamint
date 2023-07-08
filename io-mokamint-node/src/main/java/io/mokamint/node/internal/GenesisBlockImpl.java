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

import java.io.IOException;
import java.math.BigInteger;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Objects;

import io.hotmoka.crypto.Hex;
import io.hotmoka.crypto.api.HashingAlgorithm;
import io.hotmoka.marshalling.api.MarshallingContext;
import io.hotmoka.marshalling.api.UnmarshallingContext;
import io.mokamint.node.api.ConsensusConfig;
import io.mokamint.node.api.GenesisBlock;

/**
 * The implementation of a genesis block of a Mokamint blockchain.
 */
public class GenesisBlockImpl extends AbstractBlock implements GenesisBlock {

	private final LocalDateTime startDateTimeUTC;

	/**
	 * The generation signature for the block on top of the genesis block. This is arbitrary.
	 */
	private final static byte[] BLOCK_1_GENERATION_SIGNATURE = new byte[] { 13, 1, 19, 73 };

	public GenesisBlockImpl(LocalDateTime startDateTimeUTC) {
		Objects.requireNonNull(startDateTimeUTC, "startDateTimeUTC cannot be null");

		this.startDateTimeUTC = startDateTimeUTC;
	}

	/**
	 * Unmarshals a genesis block from the given context.
	 * The height of the block has been already read.
	 * 
	 * @param context the context
	 * @return the block
	 * @throws IOException if the block cannot be unmarshalled
	 */
	GenesisBlockImpl(UnmarshallingContext context) throws IOException {
		String startDateTimeUTC = context.readUTF();
		this.startDateTimeUTC = LocalDateTime.parse(startDateTimeUTC, DateTimeFormatter.ISO_LOCAL_DATE_TIME);
	}

	@Override
	public LocalDateTime getStartDateTimeUTC() {
		return startDateTimeUTC;
	}

	@Override
	public long getTotalWaitingTime() {
		return 0L; // just started
	}

	@Override
	public long getWeightedWaitingTime() {
		return 0L; // just started
	}

	@Override
	public BigInteger getAcceleration() {
		return BigInteger.valueOf(100000000000L); // big enough to start easily
	}

	@Override
	public long getHeight() {
		return 0L;
	}

	@Override
	protected byte[] getNextGenerationSignature(HashingAlgorithm<byte[]> hashing) {
		return BLOCK_1_GENERATION_SIGNATURE;
	}

	@Override
	public void into(MarshallingContext context) throws IOException {
		// we write the height of the block anyway, so that, by reading the first long,
		// it is possible to distinguish between a genesis block (height == 0)
		// and a non-genesis block (height > 0)
		context.writeLong(0L);
		context.writeUTF(DateTimeFormatter.ISO_LOCAL_DATE_TIME.format(startDateTimeUTC));
	}

	@Override
	public boolean equals(Object other) {
		return other instanceof GenesisBlock gb && startDateTimeUTC.equals(gb.getStartDateTimeUTC());
	}

	@Override
	public String toString() {
		var builder = new StringBuilder("Genesis Block:\n");
		builder.append("* creation date and time UTC: " + startDateTimeUTC + "\n");
		populate(builder);
		
		return builder.toString();
	}

	@Override
	public String toString(ConsensusConfig config, LocalDateTime startDateTimeUTC) {
		var builder = new StringBuilder("Genesis Block:\n");
		builder.append("* creation date and time UTC: " + startDateTimeUTC + "\n");
		builder.append("* hash: " + Hex.toHexString(config.getHashingForBlocks().hash(toByteArray())) + "\n");
		populate(builder);

		return builder.toString();
	}

	private void populate(StringBuilder builder) {
		builder.append("* height: " + getHeight() + "\n");
		builder.append("* total waiting time: " + getTotalWaitingTime() + "ms\n");
		builder.append("* weighted waiting time: " + getWeightedWaitingTime() + "ms\n");
		builder.append("* acceleration: " + getAcceleration() + "\n");
		builder.append("* next generation signature: " + Hex.toHexString(BLOCK_1_GENERATION_SIGNATURE));
	}
}