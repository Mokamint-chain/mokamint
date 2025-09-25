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

package io.mokamint.application.messages.internal.json;

import io.hotmoka.websockets.beans.AbstractRpcMessageJsonRepresentation;
import io.hotmoka.websockets.beans.api.InconsistentJsonException;
import io.mokamint.application.messages.api.CheckDeadlineResultMessage;
import io.mokamint.application.messages.internal.CheckDeadlineResultMessageImpl;

/**
 * The JSON representation of a {@link CheckDeadlineResultMessage}.
 */
public abstract class CheckDeadlineResultMessageJson extends AbstractRpcMessageJsonRepresentation<CheckDeadlineResultMessage> {
	private final boolean result;

	protected CheckDeadlineResultMessageJson(CheckDeadlineResultMessage message) {
		super(message);

		this.result = message.get();
	}

	public boolean getResult() {
		return result;
	}

	@Override
	public CheckDeadlineResultMessage unmap() throws InconsistentJsonException {
		return new CheckDeadlineResultMessageImpl(this);
	}

	@Override
	protected String getExpectedType() {
		return CheckDeadlineResultMessage.class.getName();
	}
}