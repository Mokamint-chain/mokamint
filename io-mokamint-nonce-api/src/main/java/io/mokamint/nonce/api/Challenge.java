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

import java.util.function.Function;

import io.hotmoka.annotations.Immutable;
import io.hotmoka.crypto.api.HashingAlgorithm;
import io.hotmoka.marshalling.api.Marshallable;

/**
 * The challenge to build a deadline. This can be provided for instance to
 * a miner to describe the properties of the deadline one is looking for.
 */
@Immutable
public interface Challenge extends Marshallable {

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
	HashingAlgorithm getHashing();

	/**
	 * Checks if this challenge matches the given one.
	 * If it doesn't, an exception is thrown by using the given supplier.
	 * 
	 * @param <E> the type of the thrown exception
	 * @param other the other challenge matched against this challenge
	 * @param exceptionSupplier the supplier of the exception: given the message, it yields the exception with that message
	 * @throws E if the match fails
	 */
	<E extends Exception> void matchesOrThrow(Challenge other, Function<String, E> exceptionSupplier) throws E;

	/**
	 * Yields a string representation of this challenge.
	 * 
	 * @return the string representation
	 */
	@Override
	String toString();

	/**
	 * A sanitized version of {@link #toString()}. It imposed a maximal length to the generation signature reported
	 * in the resulting string. This is important if the challenge comes from the network,
	 * since it might contain arbitrarily long strings that might, for instance, pollute the logs.
	 * For most challenges, this coincides with {@link #toString()}.
	 * 
	 * @return the sanitized string
	 */
	String toStringSanitized();
}