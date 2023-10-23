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
import io.mokamint.nonce.api.Deadline;

/**
 * The description of a non-genesis block of the Mokamint blockchain.
 * This is the header of a block, missing signature and transactions
 * wrt an actual block.
 */
@Immutable
public interface NonGenesisBlockDescription extends BlockDescription {

	/**
	 * Yields the deadline computed for the block.
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
	 * Checks if this block description matches another.
	 * If it doesn't, an exception is thrown by using the given supplier.
	 * 
	 * @param <E> the type of the thrown exception
	 * @param description the description matched against this
	 * @param exceptionSupplier the supplier of the exception: given the message, it yields the exception with that message
	 * @throws E if the match fails
	 */
	<E extends Exception> void matchesOrThrow(NonGenesisBlockDescription description, Function<String, E> exceptionSupplier) throws E;
}