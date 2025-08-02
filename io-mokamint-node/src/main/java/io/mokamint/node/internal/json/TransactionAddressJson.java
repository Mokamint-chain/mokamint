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

package io.mokamint.node.internal.json;

import io.hotmoka.crypto.Hex;
import io.hotmoka.websockets.beans.api.InconsistentJsonException;
import io.hotmoka.websockets.beans.api.JsonRepresentation;
import io.mokamint.node.api.TransactionAddress;
import io.mokamint.node.internal.TransactionAddressImpl;

/**
 * The JSON representation of a {@link TransactionAddress}.
 */
public abstract class TransactionAddressJson implements JsonRepresentation<TransactionAddress> {
	private final String blockHash;
	private final int progressive;

	protected TransactionAddressJson(TransactionAddress address) {
		this.blockHash = Hex.toHexString(address.getBlockHash());
		this.progressive = address.getProgressive();
	}

	public String getBlockHash() {
		return blockHash;
	}

	public int getProgressive() {
		return progressive;
	}

	@Override
	public TransactionAddress unmap() throws InconsistentJsonException {
		return new TransactionAddressImpl(this);
	}
}