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

package io.mokamint.application.messages.internal.json;

import io.hotmoka.websockets.beans.AbstractRpcMessageJsonRepresentation;
import io.hotmoka.websockets.beans.api.InconsistentJsonException;
import io.mokamint.application.Infos;
import io.mokamint.application.messages.api.GetInfoResultMessage;
import io.mokamint.application.messages.internal.GetInfoResultMessageImpl;

/**
 * The JSON representation of a {@link GetInfoResultMessage}.
 */
public abstract class GetInfoResultMessageJson extends AbstractRpcMessageJsonRepresentation<GetInfoResultMessage> {
	private final Infos.Json result;

	protected GetInfoResultMessageJson(GetInfoResultMessage message) {
		super(message);

		this.result = new Infos.Json(message.get());
	}

	public final Infos.Json getResult() {
		return result;
	}

	@Override
	public GetInfoResultMessage unmap() throws InconsistentJsonException {
		return new GetInfoResultMessageImpl(this);
	}

	@Override
	protected String getExpectedType() {
		return GetInfoResultMessage.class.getName();
	}
}