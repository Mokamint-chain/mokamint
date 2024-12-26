/*
Copyright 2024 Fausto Spoto

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

import io.hotmoka.crypto.Hex;
import io.hotmoka.crypto.HexConversionException;
import io.hotmoka.websockets.beans.AbstractRpcMessage;
import io.hotmoka.websockets.beans.api.InconsistentJsonException;
import io.mokamint.node.api.PublicNode;
import io.mokamint.node.messages.api.GetTransactionRepresentationMessage;
import io.mokamint.node.messages.internal.gson.GetTransactionRepresentationMessageJson;

/**
 * Implementation of the network message corresponding to the {@link PublicNode#getTransactionRepresentation(byte[])} method of a node.
 */
public class GetTransactionRepresentationMessageImpl extends AbstractRpcMessage implements GetTransactionRepresentationMessage {
	private final byte[] hash;

	/**
	 * Creates the message.
	 * 
	 * @param hash the {@code hash} parameter of the method
	 * @param id the identifier of the message
	 */
	public GetTransactionRepresentationMessageImpl(byte[] hash, String id) {
		super(id);

		this.hash = Objects.requireNonNull(hash, "hash cannot be null");
	}

	/**
	 * Creates a message from the given JSON representation.
	 * 
	 * @param json the JSON representation
	 * @throws InconsistentJsonException if {@code json} is inconsistent
	 */
	public GetTransactionRepresentationMessageImpl(GetTransactionRepresentationMessageJson json) throws InconsistentJsonException {
		super(json.getId());

		var hash = json.getHash();
		if (hash == null)
			throw new InconsistentJsonException("hash cannot be null");

		try {
			this.hash = Hex.fromHexString(hash);
		}
		catch (HexConversionException e) {
			throw new InconsistentJsonException(e);
		}
	}

	@Override
	public byte[] getHash() {
		return hash.clone();
	}

	@Override
	public boolean equals(Object other) {
		if (other instanceof GetTransactionRepresentationMessageImpl gtrmi) // optimization
			return super.equals(other) && Arrays.equals(hash, gtrmi.hash);
		else
			return other instanceof GetTransactionRepresentationMessage gtrm && super.equals(other) && Arrays.equals(hash, gtrm.getHash());
	}

	@Override
	protected String getExpectedType() {
		return GetTransactionRepresentationMessage.class.getName();
	}
}