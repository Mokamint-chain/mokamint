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

import java.math.BigInteger;
import java.time.LocalDateTime;
import java.util.Objects;

import io.mokamint.node.api.GenesisBlockDescription;

/**
 * The implementation of the description of a genesis block of the Mokamint blockchain.
 */
public class GenesisBlockDescriptionImpl implements GenesisBlockDescription {

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
	 * Creates a new genesis block description.
	 */
	public GenesisBlockDescriptionImpl(LocalDateTime startDateTimeUTC, BigInteger acceleration) {
		this.startDateTimeUTC = startDateTimeUTC;
		this.acceleration = acceleration;

		verify();
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
		var builder = new StringBuilder("Genesis Block:\n");
		builder.append("* creation date and time UTC: " + startDateTimeUTC + "\n");
		builder.append("* height: " + getHeight() + "\n");
		builder.append("* power: " + getPower() + "\n");
		builder.append("* total waiting time: " + getTotalWaitingTime() + " ms\n");
		builder.append("* weighted waiting time: " + getWeightedWaitingTime() + " ms\n");
		builder.append("* acceleration: " + getAcceleration() + "\n");
		
		return builder.toString();
	}
}