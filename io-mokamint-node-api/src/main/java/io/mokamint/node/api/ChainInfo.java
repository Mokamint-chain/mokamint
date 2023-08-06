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

package io.mokamint.node.api;

import java.util.Optional;

import io.hotmoka.annotations.Immutable;

/**
 * Information about the current best chain of a Mokamint node.
 */
@Immutable
public interface ChainInfo {

	/**
	 * Yields the height of the chain (number of blocks from genesis to head).
	 * 
	 * @return the height of the chain
	 */
	long getHeight();

	/**
	 * Yields the hash of the genesis block of the chain, if any.
	 * 
	 * @return the hash, if any
	 */
	Optional<byte[]> getGenesisHash();

	/**
	 * Yields the hash of the head block of the chain, if any.
	 * 
	 * @return the hash, if any
	 */
	Optional<byte[]> getHeadHash();

	@Override
	boolean equals(Object other);

	@Override
	String toString();
}