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

import java.util.function.Function;

import io.hotmoka.annotations.Immutable;

/**
 * A non-genesis block of the Mokamint blockchain.
 */
@Immutable
public interface NonGenesisBlock extends NonGenesisBlockDescription, Block {

	/**
	 * Checks if this block matches the given description.
	 * If it doesn't, an exception is thrown by using the given supplier.
	 * 
	 * @param <E> the type of the thrown exception
	 * @param description the description matched against this block
	 * @param exceptionSupplier the supplier of the exception: given the message, it yields the exception with that message
	 * @throws E if the match fails
	 */
	<E extends Exception> void matchesOrException(NonGenesisBlockDescription description, Function<String, E> exceptionSupplier) throws E;

	/**
	 * Yields the signature of this node, computed from its hash by the node
	 * that mined this block. This signature must have been computed with the
	 * private key corresponding to the node's public key inside the prolog
	 * of the deadline of this block (as returned by {@link #getDeadline()}.
	 * 
	 * @return the signature
	 */
	byte[] getSignature();
}