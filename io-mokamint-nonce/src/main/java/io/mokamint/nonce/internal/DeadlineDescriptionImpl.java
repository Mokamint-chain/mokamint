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

package io.mokamint.nonce.internal;

import java.util.Arrays;
import java.util.Objects;

import io.hotmoka.annotations.Immutable;
import io.hotmoka.crypto.Hex;
import io.hotmoka.crypto.api.HashingAlgorithm;
import io.mokamint.nonce.api.Deadline;
import io.mokamint.nonce.api.DeadlineDescription;

/**
 * Implementation of a deadline description. It reports the information needed
 * to compute a deadline from a plot file.
 */
@Immutable
public class DeadlineDescriptionImpl implements DeadlineDescription {
	private final int scoopNumber;
	private final byte[] data;
	private final HashingAlgorithm hashing;

	public DeadlineDescriptionImpl(int scoopNumber, byte[] data, HashingAlgorithm hashing) {
		Objects.requireNonNull(data, "data cannot be null");
		Objects.requireNonNull(hashing, "hashing cannot be null");

		if (scoopNumber < 0 || scoopNumber > Deadline.MAX_SCOOP_NUMBER)
			throw new IllegalArgumentException("scoopNumber must be between 0 and " + Deadline.MAX_SCOOP_NUMBER);

		this.scoopNumber = scoopNumber;
		this.data = data;
		this.hashing = hashing;
	}

	@Override
	public boolean equals(Object other) {
		return other instanceof DeadlineDescription otherAsDeadlineDescription &&
			scoopNumber == otherAsDeadlineDescription.getScoopNumber() &&
			Arrays.equals(data, otherAsDeadlineDescription.getData()) &&
			hashing.equals(otherAsDeadlineDescription.getHashing());
	}

	@Override
	public int hashCode() {
		return scoopNumber ^ Arrays.hashCode(data) ^ hashing.hashCode();
	}

	@Override
	public int getScoopNumber() {
		return scoopNumber;
	}

	@Override
	public byte[] getData() {
		return data.clone();
	}

	@Override
	public HashingAlgorithm getHashing() {
		return hashing;
	}

	@Override
	public String toString() {
		return "scoopNumber: " + scoopNumber + ", data: " + Hex.toHexString(data) + ", hashing: " + hashing;
	}
}