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

import java.security.NoSuchAlgorithmException;

import io.hotmoka.websockets.beans.AbstractRpcMessageJsonRepresentation;
import io.hotmoka.websockets.beans.api.InconsistentJsonException;
import io.mokamint.application.messages.api.CheckDeadlineMessage;
import io.mokamint.application.messages.internal.CheckDeadlineMessageImpl;
import io.mokamint.nonce.Deadlines;

/**
 * The JSON representation of an {@link CheckDeadlineMessage}.
 */
public abstract class CheckDeadlineMessageJson extends AbstractRpcMessageJsonRepresentation<CheckDeadlineMessage> {
	private final Deadlines.Json deadline;

	protected CheckDeadlineMessageJson(CheckDeadlineMessage message) {
		super(message);

		this.deadline = new Deadlines.Json(message.getDeadline());
	}

	public Deadlines.Json getDeadline() {
		return deadline;
	}

	@Override
	public CheckDeadlineMessage unmap() throws InconsistentJsonException, NoSuchAlgorithmException {
		return new CheckDeadlineMessageImpl(this);
	}

	@Override
	protected String getExpectedType() {
		return CheckDeadlineMessage.class.getName();
	}
}