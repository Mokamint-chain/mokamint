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

import java.time.LocalDateTime;
import java.util.Optional;

import io.hotmoka.crypto.Hex;
import io.hotmoka.crypto.api.HashingAlgorithm;
import io.hotmoka.marshalling.AbstractMarshallable;
import io.mokamint.node.api.BlockDescription;

/**
 * Shared code for block descriptions.
 */
public abstract class AbstractBlockDescription extends AbstractMarshallable implements BlockDescription {

	/**
	 * Fills the given builder with the information inside this description.
	 * 
	 * @param builder the builder
	 * @param hashingForGenerations the hashing algorithm used for deadline generations, if available
	 * @param hashingForBlocks the hashing algorithm used for the blocks, if available
	 * @param startDateTimeUTC the creation time of the genesis block of the chain of the block, if available
	 */
	protected void populate(StringBuilder builder, Optional<HashingAlgorithm> hashingForGenerations, Optional<HashingAlgorithm> hashingForBlocks, Optional<LocalDateTime> startDateTimeUTC) {
		builder.append("* height: " + getHeight() + "\n");
		builder.append("* power: " + getPower() + "\n");
		builder.append("* total waiting time: " + getTotalWaitingTime() + " ms\n");
		builder.append("* weighted waiting time: " + getWeightedWaitingTime() + " ms\n");
		builder.append("* acceleration: " + getAcceleration() + "\n");

		if (hashingForGenerations.isPresent())
			builder.append("* next generation signature: " + Hex.toHexString(getNextGenerationSignature(hashingForGenerations.get())) + " (" + hashingForGenerations.get() + ")\n");
	}

	/**
	 * Yields the generation signature of any block that can legally follow this block.
	 * 
	 * @param hashingForGenerations the hashing used for the generation of deadlines.
	 * @return the generation signature
	 */
	protected abstract byte[] getNextGenerationSignature(HashingAlgorithm hashingForGenerations);
}