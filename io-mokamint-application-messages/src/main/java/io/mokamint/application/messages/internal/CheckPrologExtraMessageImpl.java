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
import java.util.Objects;

import io.hotmoka.crypto.Hex;
import io.hotmoka.crypto.HexConversionException;
import io.hotmoka.websockets.beans.AbstractRpcMessage;
import io.hotmoka.websockets.beans.api.InconsistentJsonException;
import io.mokamint.application.api.Application;
import io.mokamint.application.messages.api.CheckPrologExtraMessage;
import io.mokamint.application.messages.internal.gson.CheckPrologExtraMessageJson;

/**
 * Implementation of the network message corresponding to {@link Application#checkPrologExtra(byte[])}.
 */
public class CheckPrologExtraMessageImpl extends AbstractRpcMessage implements CheckPrologExtraMessage {
	private final byte[] extra;

	/**
	 * Creates the message.
	 * 
	 * @param extra the extra, application-specific bytes of the prolog
	 * @param id the identifier of the message
	 */
	public CheckPrologExtraMessageImpl(byte[] extra, String id) {
		super(id);

		this.extra = Objects.requireNonNull(extra);
	}

	/**
	 * Creates a message from the given JSON representation.
	 * 
	 * @param json the JSON representation
	 * @throws InconsistentJsonException if {@code json} is inconsistent
	 */
	public CheckPrologExtraMessageImpl(CheckPrologExtraMessageJson json) throws InconsistentJsonException {
		super(json.getId());

		var extra = json.getExtra();
		if (extra == null)
			throw new InconsistentJsonException("extra cannot be null");

		try {
			this.extra = Hex.fromHexString(extra);
		}
		catch (HexConversionException e) {
			throw new InconsistentJsonException(e);
		}
	}

	@Override
	public byte[] getExtra() {
		return extra.clone();
	}

	@Override
	public boolean equals(Object other) {
		return other instanceof CheckPrologExtraMessage cpem && super.equals(other) && Arrays.equals(extra, cpem.getExtra());
	}

	@Override
	protected String getExpectedType() {
		return CheckPrologExtraMessage.class.getName();
	}
}