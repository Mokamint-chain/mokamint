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
import java.time.DateTimeException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Objects;
import java.util.Optional;

import io.hotmoka.crypto.api.HashingAlgorithm;
import io.hotmoka.marshalling.api.MarshallingContext;
import io.hotmoka.marshalling.api.UnmarshallingContext;
import io.mokamint.node.api.ConsensusConfig;
import io.mokamint.node.api.GenesisBlockDescription;

/**
 * The implementation of the description of a genesis block of the Mokamint blockchain.
 */
public class GenesisBlockDescriptionImpl extends AbstractBlockDescription implements GenesisBlockDescription {

	/**
	 * The moment when the block has been mined. This is the moment when the blockchain started.
	 */
	private final LocalDateTime startDateTimeUTC;

	/**
	 * A value used to divide the deadline to derive the time needed to wait for it.
	 * The higher, the shorter the time. This value changes dynamically to cope with
	 * varying mining power in the network. It is similar to Bitcoin's difficulty.
	 */
	private final BigInteger acceleration;

	/**
	 * The generation signature for the block on top of the genesis block. This is arbitrary.
	 */
	private final static byte[] BLOCK_1_GENERATION_SIGNATURE = new byte[] { 13, 1, 19, 73 };

	/**
	 * Creates a new genesis block description.
	 */
	public GenesisBlockDescriptionImpl(LocalDateTime startDateTimeUTC, BigInteger acceleration) {
		this.startDateTimeUTC = startDateTimeUTC;
		this.acceleration = acceleration;

		verify();
	}

	/**
	 * Unmarshals a genesis block.
	 * 
	 * @param context the unmarshalling context
	 * @throws IOException if unmarshalling failed
	 */
	GenesisBlockDescriptionImpl(UnmarshallingContext context) throws IOException {
		try {
			this.startDateTimeUTC = LocalDateTime.parse(context.readStringUnshared(), DateTimeFormatter.ISO_LOCAL_DATE_TIME);
			this.acceleration = context.readBigInteger();
	
			verify();
		}
		catch (RuntimeException e) {
			throw new IOException(e);
		}
	}

	/**
	 * Checks all constraints expected from a non-genesis block.
	 * 
	 * @throws NullPointerException if some value is unexpectedly {@code null}
	 * @throws IllegalArgumentException if some value is illegal
	 */
	private void verify() {
		Objects.requireNonNull(startDateTimeUTC, "startDateTimeUTC cannot be null");
		Objects.requireNonNull(acceleration, "acceleration cannot be null");
		if (acceleration.signum() <= 0)
			throw new IllegalArgumentException("acceleration must be strictly positive");
	}

	@Override
	public BigInteger getPower() {
		return BigInteger.ZERO; // just started
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
		return acceleration;
	}

	@Override
	public long getHeight() {
		return 0L;
	}

	@Override
	public LocalDateTime getStartDateTimeUTC() {
		return startDateTimeUTC;
	}

	@Override
	protected byte[] getNextGenerationSignature(HashingAlgorithm hashing) {
		return BLOCK_1_GENERATION_SIGNATURE;
	}

	@Override
	public boolean equals(Object other) {
		return other instanceof GenesisBlockDescription gbd &&
			startDateTimeUTC.equals(gbd.getStartDateTimeUTC()) &&
			acceleration.equals(gbd.getAcceleration());
	}

	@Override
	public int hashCode() {
		return startDateTimeUTC.hashCode() ^ acceleration.hashCode();
	}

	@Override
	public String toString() {
		var builder = new StringBuilder("Genesis block:\n");
		populate(builder, Optional.empty(), Optional.empty(), Optional.empty());
		return builder.toString();
	}

	@Override
	public String toString(ConsensusConfig<?,?> config, LocalDateTime startDateTimeUTC) {
		var hashing = config.getHashingForBlocks();
		var builder = new StringBuilder("Genesis block:\n");
		populate(builder, Optional.of(config.getHashingForGenerations()), Optional.of(hashing), Optional.of(startDateTimeUTC));
		return builder.toString();
	}

	@Override
	protected void populate(StringBuilder builder, Optional<HashingAlgorithm> hashingForGenerations, Optional<HashingAlgorithm> hashingForBlocks, Optional<LocalDateTime> startDateTimeUTC) {
		builder.append("* creation date and time UTC: " + this.startDateTimeUTC + "\n");
		super.populate(builder, hashingForGenerations, hashingForBlocks, startDateTimeUTC);
	}

	@Override
	public void into(MarshallingContext context) throws IOException {
		try {
			context.writeStringUnshared(DateTimeFormatter.ISO_LOCAL_DATE_TIME.format(startDateTimeUTC));
		}
		catch (DateTimeException e) {
			throw new IOException(e);
		}

		context.writeBigInteger(acceleration);
	}
}