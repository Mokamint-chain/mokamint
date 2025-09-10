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

package io.mokamint.miner.messages.internal.json;

import java.security.NoSuchAlgorithmException;

import io.hotmoka.websockets.beans.AbstractRpcMessageJsonRepresentation;
import io.hotmoka.websockets.beans.api.InconsistentJsonException;
import io.mokamint.miner.messages.api.GetBalanceMessage;
import io.mokamint.miner.messages.internal.GetBalanceMessageImpl;

/**
 * The JSON representation of an {@link GetBalanceMessage}.
 */
public abstract class GetBalanceMessageJson extends AbstractRpcMessageJsonRepresentation<GetBalanceMessage> {
	private final String signature;
	private final String publicKey;

	protected GetBalanceMessageJson(GetBalanceMessage message) {
		super(message);

		this.signature = message.getSignature().getName();
		this.publicKey = message.getPublicKeyBase58();
	}

	@Override
	public GetBalanceMessage unmap() throws InconsistentJsonException, NoSuchAlgorithmException {
		return new GetBalanceMessageImpl(this);
	}

	@Override
	protected String getExpectedType() {
		return GetBalanceMessage.class.getName();
	}

	public String getSignature() {
		return signature;
	}

	public String getPublicKey() {
		return publicKey;
	}
}