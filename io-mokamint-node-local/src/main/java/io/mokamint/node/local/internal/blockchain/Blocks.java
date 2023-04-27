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

import io.hotmoka.marshalling.api.UnmarshallingContext;
import io.mokamint.node.api.Block;
import io.mokamint.nonce.api.Deadline;

/**
 * Providers of blocks.
 */
public interface Blocks {

	static NonGenesisBlock of(long blockNumber, long totalWaitingTime, long weightedWaitingTime, BigInteger acceleration,
			Deadline deadline, byte[] hashOfPreviousBlock) {
		return new NonGenesisBlock(blockNumber, totalWaitingTime, weightedWaitingTime, acceleration, deadline, hashOfPreviousBlock);
	}

	static GenesisBlock genesis(LocalDateTime startDateTimeUTC) {
		return new GenesisBlock(startDateTimeUTC);
	}

	/**
	 * Unmarshals a block from the given context.
	 * 
	 * @param context the context
	 * @return the block
	 */
	static Block from(UnmarshallingContext context) {
		return AbstractBlock.from(context);
	}
}
