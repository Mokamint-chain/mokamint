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

import io.hotmoka.annotations.Immutable;
import io.mokamint.nonce.api.Deadline;

/**
 * A non-genesis block of the Mokamint blockchain.
 */
@Immutable
public non-sealed interface NonGenesisBlock extends Block {

	/**
	 * Yields the deadline computed for this block.
	 * 
	 * @return the deadline
	 */
	Deadline getDeadline();

	/**
	 * Yields the reference to the previous block.
	 * 
	 * @return the reference to the previous block
	 */
	byte[] getHashOfPreviousBlock();

	/**
	 * Yields the signature of this node, computed from its hash by the node
	 * that mined this block. This signature must have been computed with the
	 * private key corresponding to the node's public key inside the prolog
	 * of the deadline of this block (as returned by {@link #getDeadline()}.
	 * 
	 * @return the signature
	 */
	//TODO byte[] getSignature();
}