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

import io.hotmoka.websockets.beans.AbstractRpcMessageJsonRepresentation;
import io.mokamint.application.messages.KeepFromMessages;
import io.mokamint.application.messages.api.KeepFromMessage;

/**
 * The JSON representation of an {@link KeepFromMessage}.
 */
public abstract class KeepFromMessageJson extends AbstractRpcMessageJsonRepresentation<KeepFromMessage> {
	private final String start;

	protected KeepFromMessageJson(KeepFromMessage message) {
		super(message);

		this.start = ISO_LOCAL_DATE_TIME.format(message.getStart());
	}

	@Override
	public KeepFromMessage unmap() {
		return KeepFromMessages.of(LocalDateTime.parse(start, ISO_LOCAL_DATE_TIME), getId());
	}

	@Override
	protected String getExpectedType() {
		return KeepFromMessage.class.getName();
	}
}