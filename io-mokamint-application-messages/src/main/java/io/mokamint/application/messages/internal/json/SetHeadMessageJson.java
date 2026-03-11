/*
Copyright 2026 Fausto Spoto

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

package io.mokamint.application.messages.internal.json;

import io.hotmoka.crypto.Hex;
import io.hotmoka.websockets.beans.AbstractRpcMessageJsonRepresentation;
import io.hotmoka.websockets.beans.api.InconsistentJsonException;
import io.mokamint.application.messages.api.SetHeadMessage;
import io.mokamint.application.messages.internal.SetHeadMessageImpl;

/**
 * The JSON representation of an {@link SetHeadMessage}.
 */
public abstract class SetHeadMessageJson extends AbstractRpcMessageJsonRepresentation<SetHeadMessage> {
	private final String stateId;

	protected SetHeadMessageJson(SetHeadMessage message) {
		super(message);

		this.stateId = Hex.toHexString(message.getStateId());
	}

	public String getStateId() {
		return stateId;
	}

	@Override
	public SetHeadMessage unmap() throws InconsistentJsonException {
		return new SetHeadMessageImpl(this);
	}

	@Override
	protected String getExpectedType() {
		return SetHeadMessage.class.getName();
	}
}