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

package io.mokamint.node.local.internal.blockchain;

import java.math.BigInteger;
import java.time.LocalDateTime;

import io.hotmoka.crypto.api.HashingAlgorithm;

/**
 * The genesis block of a Mokamint blockchain.
 */
public class GenesisBlock extends AbstractBlock {

	private final LocalDateTime startDateTimeUTC;

	/**
	 * The generation signature for the block on top of the genesis block. This is arbitrary.
	 */
	private final static byte[] BLOCK_1_GENERATION_SIGNATURE = new byte[] { 13, 1, 19, 73 };

	GenesisBlock(LocalDateTime startDateTimeUTC) {
		this.startDateTimeUTC = startDateTimeUTC;
	}

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
	public byte[] getNewGenerationSignature(HashingAlgorithm<byte[]> hashing) {
		return BLOCK_1_GENERATION_SIGNATURE.clone();
	}
}