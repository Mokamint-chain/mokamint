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

package io.mokamint.node.messages.internal.json;

import java.util.Optional;

import io.hotmoka.websockets.beans.AbstractRpcMessageJsonRepresentation;
import io.hotmoka.websockets.beans.api.InconsistentJsonException;
import io.mokamint.node.api.Request;
import io.mokamint.node.messages.api.GetRequestResultMessage;
import io.mokamint.node.messages.internal.GetRequestResultMessageImpl;

/**
 * The JSON representation of a {@link GetRequestResultMessage}.
 */
public abstract class GetRequestResultMessageJson extends AbstractRpcMessageJsonRepresentation<GetRequestResultMessage> {
	private final String request;

	protected GetRequestResultMessageJson(GetRequestResultMessage message) {
		super(message);

		this.request = message.get().map(Request::toBase64String).orElse(null);
	}

	public Optional<String> getRequest() {
		return Optional.ofNullable(request);
	}

	@Override
	public GetRequestResultMessage unmap() throws InconsistentJsonException {
		return new GetRequestResultMessageImpl(this);
	}

	@Override
	protected String getExpectedType() {
		return GetRequestResultMessage.class.getName();
	}
}