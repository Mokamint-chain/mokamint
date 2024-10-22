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
import java.math.BigInteger;

import io.hotmoka.annotations.Immutable;
import io.hotmoka.marshalling.api.Marshallable;
import io.hotmoka.marshalling.api.MarshallingContext;

/**
 * A deadline inside a plot file. It is a reference to a nonce
 * and a value computed for that nonce. Deadlines are ordered
 * by the lexicographical ordering of their values.
 */
@Immutable
public interface Deadline extends Marshallable {

	/**
	 * Yields the prolog that was used to create the plot file from which
	 * this deadline has been generated.
	 * 
	 * @return the prolog
	 */
	Prolog getProlog();

	/**
	 * Yields the progressive number of the nonce of the deadline.
	 * 
	 * @return the progressive number
	 */
	long getProgressive();

	/**
	 * Yields the value of the deadline computed for the nonce {@link #getProgressive()}
	 * of the plot file.
	 * 
	 * @return the value
	 */
	byte[] getValue();

	/**
	 * Yields the challenge this deadline responds to.
	 * 
	 * @return the challenge this deadline responds to
	 */
	Challenge getChallenge();

	/**
	 * The signature of the deadline. This has been computed with the
	 * private key corresponding to {@link Prolog#getPublicKeyForSigningDeadlines()}
	 * in the prolog.
	 * 
	 * @return the signature
	 */
	byte[] getSignature();

	/**
	 * Yields the milliseconds to wait for this deadline, assuming the given acceleration.
	 * 
	 * @param acceleration the acceleration
	 * @return the milliseconds to wait
	 */
	long getMillisecondsToWait(BigInteger acceleration);

	@Override
	boolean equals(Object other);

	@Override
	int hashCode();

	/**
	 * Compares the value of this deadline with that of another deadline.
	 * They are assumed to have been generated with the same hashing algorithm.
	 * 
	 * @param other the other deadline
	 * @return negative if the value of this deadline is smaller than the
	 *         value of the other deadline; positive if the value of this deadline
	 *         if larger than the value of the other deadline; 0 if the value are equal
	 */
	int compareByValue(Deadline other);

	/**
	 * Determines if this deadline is valid, that is, its corresponding nonce
	 * is for the same deadline.
	 * 
	 * @return true if and only if this deadline is valid
	 */
	boolean isValid();

	/**
	 * Yields the power of this deadline. This is the ratio between the worst possible deadline value
	 * and the actual deadline value.
	 * 
	 * @return the power of this deadline
	 */
	BigInteger getPower();

	/**
	 * Marshals this object into a given stream. This method in general
	 * performs better than standard Java serialization, wrt the size of the marshalled data.
	 * It does not marshal information that can be recovered from the configuration of
	 * a Mokamint node storing this deadline.
	 * 
	 * @param context the context holding the stream
	 * @throws IOException if this object cannot be marshalled
	 */
	void intoWithoutConfigurationData(MarshallingContext context) throws IOException;

	/**
	 * Yields a string representation of this deadline.
	 * 
	 * @return the string representation
	 */
	@Override
	String toString();
}