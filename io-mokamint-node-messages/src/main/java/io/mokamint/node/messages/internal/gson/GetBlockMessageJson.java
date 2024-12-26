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

package io.mokamint.node.messages.internal.gson;

import io.hotmoka.crypto.Hex;
import io.hotmoka.websockets.beans.AbstractRpcMessageJsonRepresentation;
import io.hotmoka.websockets.beans.api.InconsistentJsonException;
import io.mokamint.node.messages.api.GetBlockMessage;
import io.mokamint.node.messages.internal.GetBlockMessageImpl;

/**
 * The JSON representation of a {@link GetBlockMessage}.
 */
public abstract class GetBlockMessageJson extends AbstractRpcMessageJsonRepresentation<GetBlockMessage> {
	private final String hash;

	protected GetBlockMessageJson(GetBlockMessage message) {
		super(message);

		this.hash = Hex.toHexString(message.getHash());
	}

	public String getHash() {
		return hash;
	}

	@Override
	public GetBlockMessage unmap() throws InconsistentJsonException {
		return new GetBlockMessageImpl(this);
	}

	@Override
	protected String getExpectedType() {
		return GetBlockMessage.class.getName();
	}
}