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

package io.mokamint.plotter.api;

import java.security.PublicKey;

import io.hotmoka.annotations.Immutable;
import io.hotmoka.marshalling.api.Marshallable;

/**
 * The prolog of a plot file.
 */
@Immutable
public interface Prolog extends Marshallable {

	/**
	 * The maximal length of the prolog, in bytes (inclusive).
	 */
	final int MAX_PROLOG_SIZE = 16777216; // 16 megabytes

	/**
	 * Yields the chain identifier of the blockchain where the plot with
	 * this prolog can be used.
	 * 
	 * @return the chain identifier
	 */
	String getChainId();

	/**
	 * Yields the public key that the node using the plots with this prolog
	 * uses to sign new mined blocks.
	 * 
	 * @return the public key
	 */
	PublicKey getNodePublicKey();

	/**
	 * Yields the public key that identifier the plots having this prolog.
	 * 
	 * @return the public key
	 */
	PublicKey getPlotPublicKey();

	/**
	 * Application-specific extra data in the prolog.
	 * 
	 * @return the extra data
	 */
	byte[] getExtra();

	@Override
	String toString();

	@Override
	boolean equals(Object other);

	@Override
	int hashCode();
}