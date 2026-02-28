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
import io.mokamint.node.messages.api.GetRequestRepresentationResultMessage;
import io.mokamint.node.messages.internal.GetRequestRepresentationResultMessageImpl;

/**
 * The JSON representation of a {@link GetRequestRepresentationResultMessage}.
 */
public abstract class GetRequestRepresentationResultMessageJson extends AbstractRpcMessageJsonRepresentation<GetRequestRepresentationResultMessage> {
	private final String representation;

	protected GetRequestRepresentationResultMessageJson(GetRequestRepresentationResultMessage message) {
		super(message);

		this.representation = message.get().orElse(null);
	}

	public Optional<String> getRepresentation() {
		return Optional.ofNullable(representation);
	}

	@Override
	public GetRequestRepresentationResultMessage unmap() {
		return new GetRequestRepresentationResultMessageImpl(this);
	}

	@Override
	protected String getExpectedType() {
		return GetRequestRepresentationResultMessage.class.getName();
	}
}