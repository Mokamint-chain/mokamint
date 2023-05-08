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

package io.mokamint.node;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.math.BigInteger;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;

import io.hotmoka.exceptions.UncheckedIOException;
import io.hotmoka.marshalling.UnmarshallingContexts;
import io.hotmoka.marshalling.api.UnmarshallingContext;
import io.mokamint.node.api.Block;
import io.mokamint.node.api.GenesisBlock;
import io.mokamint.node.api.NonGenesisBlock;
import io.mokamint.node.internal.AbstractBlock;
import io.mokamint.node.internal.GenesisBlockImpl;
import io.mokamint.node.internal.NonGenesisBlockImpl;
import io.mokamint.nonce.api.Deadline;

/**
 * Providers of blocks.
 */
public interface Blocks {

	/**
	 * Yields a new non-genesis block.
	 * 
	 * @param blockNumber
	 * @param totalWaitingTime
	 * @param weightedWaitingTime
	 * @param acceleration
	 * @param deadline
	 * @param hashOfPreviousBlock
	 * @return the non-genesis block
	 */
	static NonGenesisBlock of(long blockNumber, long totalWaitingTime, long weightedWaitingTime, BigInteger acceleration,
			Deadline deadline, byte[] hashOfPreviousBlock) {
		return new NonGenesisBlockImpl(blockNumber, totalWaitingTime, weightedWaitingTime, acceleration, deadline, hashOfPreviousBlock);
	}

	/**
	 * Yields a new genesis block.
	 * 
	 * @param startDateTimeUTC the moment when the block has been created
	 * @return the genesis block
	 */
	static GenesisBlock genesis(LocalDateTime startDateTimeUTC) {
		return new GenesisBlockImpl(startDateTimeUTC);
	}

	/**
	 * Unmarshals a block from the given context.
	 * 
	 * @param context the context
	 * @return the block
	 * @throws NoSuchAlgorithmException if the hashing algorithm of the block is unknown
	 */
	static Block from(UnmarshallingContext context) throws NoSuchAlgorithmException {
		return AbstractBlock.from(context);
	}

	/**
	 * Unmarshals a block from the given bytes.
	 * 
	 * @param bytes the bytes
	 * @return the block
	 * @throws NoSuchAlgorithmException if the hashing algorithm of the block is unknown
	 */
	static Block from(byte[] bytes) throws NoSuchAlgorithmException {
		try (var bais = new ByteArrayInputStream(bytes); var context = UnmarshallingContexts.of(bais)) {
			return from(context);
		}
		catch (IOException e) {
			throw new UncheckedIOException(e);
		}
	}
}