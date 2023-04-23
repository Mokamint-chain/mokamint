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

import java.io.IOException;
import java.math.BigInteger;

import io.hotmoka.crypto.api.HashingAlgorithm;
import io.hotmoka.marshalling.api.MarshallingContext;
import io.mokamint.nonce.api.Deadline;

/**
 * A non-genesis block of the Mokamint blockchain.
 */
public class NonGenesisBlock extends AbstractBlock {

	/**
	 * The total waiting time for the construction of the blockchain, from
	 * the genesis block to this block, excluded.
	 */
	private final long totalWaitingTime;

	/**
	 * The weighted waiting time for the construction of the blockchain until
	 * this block (excluded).
	 */
	private final long weightedWaitingTime;

	/**
	 * The block height, non-negative, counting from 0, which is the genesis block.
	 */
	private final long height;

	/**
	 * A value used to divide the deadline to derive the time needed to wait for it.
	 * The higher, the shorter the time. This value changes dynamically to cope with
	 * varying mining power in the network. It is the inverse of Bitcoin's difficulty.
	 */
	private final BigInteger acceleration;

	/**
	 * The deadline computed for this block.
	 */
	private final Deadline deadline;

	NonGenesisBlock(long height, long totalWaitingTime, long weightedWaitingTime, BigInteger acceleration, Deadline deadline) {
		this.height = height;
		this.totalWaitingTime = totalWaitingTime;
		this.weightedWaitingTime = weightedWaitingTime;
		this.acceleration = acceleration;
		this.deadline = deadline;
	}

	@Override
	public long getTotalWaitingTime() {
		return totalWaitingTime;
	}

	@Override
	public long getWeightedWaitingTime() {
		return weightedWaitingTime;
	}

	@Override
	public BigInteger getAcceleration() {
		return acceleration;
	}

	@Override
	public long getHeight() {
		return height;
	}

	public Deadline getDeadline() {
		return deadline;
	}

	@Override
	public byte[] getNewGenerationSignature(HashingAlgorithm<byte[]> hashing) {
		byte[] previousGenerationSignature = deadline.getData();
		byte[] previousProlog = deadline.getProlog();
		byte[] merge = concat(previousGenerationSignature, previousProlog);
		return hashing.hash(merge);
	}

	@Override
	public void into(MarshallingContext context) throws IOException {
		// TODO Auto-generated method stub
	}
}