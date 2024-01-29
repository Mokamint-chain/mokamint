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

package io.mokamint.application.messages.internal.gson;

import io.hotmoka.crypto.Hex;
import io.hotmoka.crypto.HexConversionException;
import io.hotmoka.websockets.beans.AbstractRpcMessageJsonRepresentation;
import io.mokamint.application.messages.CheckPrologExtraMessages;
import io.mokamint.application.messages.api.CheckPrologExtraMessage;

/**
 * The JSON representation of an {@link CheckPrologExtraMessage}.
 */
public abstract class CheckPrologExtraMessageJson extends AbstractRpcMessageJsonRepresentation<CheckPrologExtraMessage> {
	private final String extra;

	protected CheckPrologExtraMessageJson(CheckPrologExtraMessage message) {
		super(message);

		this.extra = Hex.toHexString(message.getExtra());
	}

	@Override
	public CheckPrologExtraMessage unmap() throws HexConversionException {
		return CheckPrologExtraMessages.of(Hex.fromHexString(extra), getId());
	}

	@Override
	protected String getExpectedType() {
		return CheckPrologExtraMessage.class.getName();
	}
}