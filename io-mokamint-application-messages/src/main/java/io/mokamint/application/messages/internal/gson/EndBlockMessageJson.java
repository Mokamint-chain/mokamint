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

import java.security.NoSuchAlgorithmException;

import io.hotmoka.websockets.beans.AbstractRpcMessageJsonRepresentation;
import io.hotmoka.websockets.beans.api.InconsistentJsonException;
import io.mokamint.application.messages.api.EndBlockMessage;
import io.mokamint.application.messages.internal.EndBlockMessageImpl;
import io.mokamint.nonce.Deadlines;

/**
 * The JSON representation of an {@link EndBlockMessage}.
 */
public abstract class EndBlockMessageJson extends AbstractRpcMessageJsonRepresentation<EndBlockMessage> {
	private final Deadlines.Json deadline;
	private final int groupId;

	protected EndBlockMessageJson(EndBlockMessage message) {
		super(message);

		this.deadline = new Deadlines.Json(message.getDeadline());
		this.groupId = message.getGroupId();
	}

	public Deadlines.Json getDeadline() {
		return deadline;
	}

	public int getGroupId() {
		return groupId;
	}

	@Override
	public EndBlockMessage unmap() throws NoSuchAlgorithmException, InconsistentJsonException {
		return new EndBlockMessageImpl(this);
	}

	@Override
	protected String getExpectedType() {
		return EndBlockMessage.class.getName();
	}
}