/*
Copyright 2025 Fausto Spoto

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

package io.mokamint.application.messages.internal;

import java.security.NoSuchAlgorithmException;

import io.hotmoka.exceptions.ExceptionSupplierFromMessage;
import io.hotmoka.exceptions.Objects;
import io.hotmoka.websockets.beans.AbstractRpcMessage;
import io.hotmoka.websockets.beans.api.InconsistentJsonException;
import io.mokamint.application.api.Application;
import io.mokamint.application.messages.api.PublishMessage;
import io.mokamint.application.messages.internal.json.PublishMessageJson;
import io.mokamint.node.api.Block;

/**
 * Implementation of the network message corresponding to {@link Application#publish(Block)}.
 */
public class PublishMessageImpl extends AbstractRpcMessage implements PublishMessage {
	private final Block block;

	/**
	 * Creates the message.
	 * 
	 * @param block the block in the message
	 * @param id the identifier of the message
	 */
	public PublishMessageImpl(Block block, String id) {
		this(block, id, IllegalArgumentException::new);
	}

	/**
	 * Creates a message from the given JSON representation.
	 * 
	 * @param json the JSON representation
	 * @throws InconsistentJsonException if the JSON representation is inconsistent
	 * @throws NoSuchAlgorithmException if {@code json} refers to an unknown cryptographic algorithm
	 */
	public PublishMessageImpl(PublishMessageJson json) throws InconsistentJsonException, NoSuchAlgorithmException {
		this(
			Objects.requireNonNull(json.getBlock(), "block cannot be null", InconsistentJsonException::new).unmap(),
			json.getId(),
			InconsistentJsonException::new
		);
	}

	/**
	 * Creates the message.
	 * 
	 * @param <E> the type of the exception thrown if some argument is illegal
	 * @param block the block that must be published
	 * @param id the identifier of the message
	 * @param onIllegalArgs the creator of the exception thrown if some argument is illegal
	 * @throws E if some argument is illegal
	 */
	private <E extends Exception> PublishMessageImpl(Block block, String id, ExceptionSupplierFromMessage<? extends E> onIllegalArgs) throws E {
		super(id, onIllegalArgs);
	
		this.block = Objects.requireNonNull(block, "block cannot be null", onIllegalArgs);
	}

	@Override
	public Block getBlock() {
		return block;
	}

	@Override
	public boolean equals(Object other) {
		return other instanceof PublishMessage pm && super.equals(other) && block.equals(pm.getBlock());
	}

	@Override
	protected String getExpectedType() {
		return PublishMessage.class.getName();
	}
}