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

package io.mokamint.application.messages.internal;

import java.util.Arrays;

import io.hotmoka.crypto.Hex;
import io.hotmoka.crypto.HexConversionException;
import io.hotmoka.websockets.beans.AbstractRpcMessage;
import io.hotmoka.websockets.beans.api.InconsistentJsonException;
import io.mokamint.application.api.Application;
import io.mokamint.application.messages.api.EndBlockResultMessage;
import io.mokamint.application.messages.internal.gson.EndBlockResultMessageJson;
import io.mokamint.nonce.api.Deadline;

/**
 * Implementation of the network message corresponding to the result of the {@link Application#endBlock(int, Deadline)} method.
 */
public class EndBlockResultMessageImpl extends AbstractRpcMessage implements EndBlockResultMessage {

	/**
	 * The result of the call.
	 */
	private final byte[] result;

	/**
	 * Creates the message.
	 * 
	 * @param result the result of the call
	 * @param id the identifier of the message
	 */
	public EndBlockResultMessageImpl(byte[] result, String id) {
		super(id);

		this.result = result.clone();
	}

	/**
	 * Creates a message from its JSOn representation.
	 * 
	 * @param json the JSON representation
	 * @throws InconsistentJsonException if {@code json} is inconsistent
	 */
	public EndBlockResultMessageImpl(EndBlockResultMessageJson json) throws InconsistentJsonException {
		super(json.getId());

		var result = json.getResult();
		if (result == null)
			throw new InconsistentJsonException("result cannot be null");

		try {
			this.result = Hex.fromHexString(result);
		}
		catch (HexConversionException e) {
			throw new InconsistentJsonException(e);
		}
	}

	@Override
	public boolean equals(Object other) {
		return other instanceof EndBlockResultMessage ebrm && super.equals(other) && Arrays.equals(result, ebrm.get());
	}

	@Override
	protected String getExpectedType() {
		return EndBlockResultMessage.class.getName();
	}

	@Override
	public byte[] get() {
		return result.clone();
	}
}