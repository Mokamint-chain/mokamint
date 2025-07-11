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

package io.mokamint.nonce.api;

import java.io.IOException;

import io.hotmoka.annotations.Immutable;
import io.hotmoka.crypto.api.HashingAlgorithm;
import io.hotmoka.marshalling.api.Marshallable;
import io.hotmoka.marshalling.api.MarshallingContext;

/**
 * The challenge to build a deadline. This can be provided for instance to
 * a miner to describe the properties of the deadline one is looking for.
 */
@Immutable
public interface Challenge extends Marshallable {

	/**
	 * The number of scoops in a nonce.
	 */
	final int SCOOPS_PER_NONCE = 4096;

	/**
	 * Yields the number of the scoop considered to compute the deadline.
	 * 
	 * @return the number of the scoop
	 */
	int getScoopNumber();

	/**
	 * Yields the generation signature used to compute the deadline.
	 * 
	 * @return the generation signature
	 */
	byte[] getGenerationSignature();

	/**
	 * The hashing algorithm used for generating the deadline.
	 * 
	 * @return the hashing algorithm
	 */
	HashingAlgorithm getHashingForDeadlines();

	/**
	 * The hashing algorithm used for the generation signatures.
	 * 
	 * @return the hashing algorithm
	 */
	HashingAlgorithm getHashingForGenerations();

	/**
	 * Checks if this challenge matches the given one.
	 * If it doesn't, an exception is thrown.
	 * 
	 * @param other the other challenge matched against this challenge
	 * @throws ChallengeMatchException if the match fails
	 */
	void requireMatches(Challenge other) throws ChallengeMatchException;

	/**
	 * Marshals this object into a given stream. This method in general
	 * performs better than standard Java serialization, wrt the size of the marshalled data.
	 * It does not report information that can be recomputed from the configuration of the
	 * node storing this challenge.
	 * 
	 * @param context the context holding the stream
	 * @throws IOException if this object cannot be marshalled
	 */
	void intoWithoutConfigurationData(MarshallingContext context) throws IOException;

	/**
	 * Yields a string representation of this challenge.
	 * 
	 * @return the string representation
	 */
	@Override
	String toString();
}