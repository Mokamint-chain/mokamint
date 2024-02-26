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

import static java.time.format.DateTimeFormatter.ISO_LOCAL_DATE_TIME;

import java.time.LocalDateTime;

import io.hotmoka.crypto.Hex;
import io.hotmoka.crypto.HexConversionException;
import io.hotmoka.websockets.beans.AbstractRpcMessageJsonRepresentation;
import io.mokamint.application.messages.BeginBlockMessages;
import io.mokamint.application.messages.api.BeginBlockMessage;

/**
 * The JSON representation of an {@link BeginBlockMessage}.
 */
public abstract class BeginBlockMessageJson extends AbstractRpcMessageJsonRepresentation<BeginBlockMessage> {
	private final long height;
	private final String stateId;
	private final String when;

	protected BeginBlockMessageJson(BeginBlockMessage message) {
		super(message);

		this.height = message.getHeight();
		this.stateId = Hex.toHexString(message.getStateId());
		this.when = ISO_LOCAL_DATE_TIME.format(message.getWhen());
	}

	@Override
	public BeginBlockMessage unmap() throws HexConversionException {
		return BeginBlockMessages.of(height, LocalDateTime.parse(when, ISO_LOCAL_DATE_TIME), Hex.fromHexString(stateId), getId());
	}

	@Override
	protected String getExpectedType() {
		return BeginBlockMessage.class.getName();
	}
}