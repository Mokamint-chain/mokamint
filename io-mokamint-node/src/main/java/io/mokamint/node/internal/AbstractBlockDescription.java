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

/**
 * 
 */

package io.mokamint.node.internal;

import java.io.IOException;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.Optional;

import io.hotmoka.crypto.Hex;
import io.hotmoka.crypto.api.HashingAlgorithm;
import io.hotmoka.marshalling.AbstractMarshallable;
import io.hotmoka.marshalling.api.UnmarshallingContext;
import io.mokamint.node.api.Block;
import io.mokamint.node.api.BlockDescription;
import io.mokamint.node.api.ConsensusConfig;

/**
 * Shared code for block descriptions.
 */
public abstract class AbstractBlockDescription extends AbstractMarshallable implements BlockDescription {

	/**
	 * Unmarshals a block description from the given context.
	 * 
	 * @param context the context
	 * @return the block description
	 * @throws NoSuchAlgorithmException if some hashing or signature algorithm is not available
	 * @throws IOException if the block description cannot be unmarshalled
	 */
	public static AbstractBlockDescription from(UnmarshallingContext context) throws NoSuchAlgorithmException, IOException {
		// by reading the height, we can determine if it's a genesis block description or not
		var height = context.readLong();
		if (height == 0L)
			return new GenesisBlockDescriptionImpl(context);
		else
			return new NonGenesisBlockDescriptionImpl(height, context);
	}

	/**
	 * Unmarshals information from the given context, so that a block
	 * can be unmarshalled from this description and the context.
	 * 
	 * @param context the context
	 * @return the block, having this description
	 * @throws IOException if unmarshalling fails
	 */
	protected abstract Block unmarshals(UnmarshallingContext context) throws NoSuchAlgorithmException, IOException;

	@Override
	public void populate(StringBuilder builder, Optional<HashingAlgorithm> hashingForGenerations, Optional<HashingAlgorithm> hashingForBlocks, Optional<LocalDateTime> startDateTimeUTC) {
		builder.append("* height: " + getHeight() + "\n");
		builder.append("* power: " + getPower() + "\n");
		builder.append("* total waiting time: " + getTotalWaitingTime() + " ms\n");
		builder.append("* weighted waiting time: " + getWeightedWaitingTime() + " ms\n");
		builder.append("* acceleration: " + getAcceleration() + "\n");
		hashingForGenerations.ifPresent(h -> builder.append("* next generation signature: " + Hex.toHexString(getNextGenerationSignature(h)) + " (" + h + ")\n"));
	}

	@Override
	public final String toString() {
		var builder = new StringBuilder(nameInToString() + ":\n");
		populate(builder, Optional.empty(), Optional.empty(), Optional.empty());
		return builder.toString();
	}

	@Override
	public final String toString(ConsensusConfig<?,?> config, byte[] hash, LocalDateTime startDateTimeUTC) {
		var builder = new StringBuilder(nameInToString() + " with hash " + Hex.toHexString(hash) + " (" + config.getHashingForBlocks() + "):\n");
		populate(builder, Optional.of(config.getHashingForGenerations()), Optional.of(config.getHashingForBlocks()), Optional.of(startDateTimeUTC));
		return builder.toString();
	}

	/**
	 * Yields the name to use to describe this block in the result of {@link #toString()}.
	 * 
	 * @return the name
	 */
	protected abstract String nameInToString();
}