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

package io.mokamint.node.messages.internal;

import java.util.Arrays;
import java.util.Objects;

import io.hotmoka.websockets.beans.AbstractRpcMessage;
import io.mokamint.node.api.PublicNode;
import io.mokamint.node.messages.api.GetBlockDescriptionMessage;

/**
 * Implementation of the network message corresponding to the {@link PublicNode#getBlockDescription(byte[])} method of a node.
 */
public class GetBlockDescriptionMessageImpl extends AbstractRpcMessage implements GetBlockDescriptionMessage {
	private final byte[] hash;

	/**
	 * Creates the message.
	 * 
	 * @param hash the {@code hash} parameter of the method
	 * @param id the identifier of the message
	 */
	public GetBlockDescriptionMessageImpl(byte[] hash, String id) {
		super(id);

		this.hash = Objects.requireNonNull(hash, "hash cannot be null");
	}

	@Override
	public byte[] getHash() {
		return hash.clone();
	}

	@Override
	public boolean equals(Object other) {
		return other instanceof GetBlockDescriptionMessage gbdm && super.equals(other) && Arrays.equals(hash, gbdm.getHash());
	}

	@Override
	protected String getExpectedType() {
		return GetBlockDescriptionMessage.class.getName();
	}
}