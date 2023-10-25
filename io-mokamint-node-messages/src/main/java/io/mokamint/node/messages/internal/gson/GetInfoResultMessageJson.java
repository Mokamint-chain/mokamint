/*
Copyright 2023 Fausto Spoto

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

package io.mokamint.node.messages.internal.gson;

import io.hotmoka.websockets.beans.AbstractRpcMessageJsonRepresentation;
import io.mokamint.node.NodeInfos;
import io.mokamint.node.messages.GetInfoResultMessages;
import io.mokamint.node.messages.api.GetInfoResultMessage;

/**
 * The JSON representation of a {@link GetInfoResultMessage}.
 */
public abstract class GetInfoResultMessageJson extends AbstractRpcMessageJsonRepresentation<GetInfoResultMessage> {
	private final NodeInfos.Json info;

	protected GetInfoResultMessageJson(GetInfoResultMessage message) {
		super(message);

		this.info = new NodeInfos.Json(message.get());
	}

	@Override
	public GetInfoResultMessage unmap() {
		return GetInfoResultMessages.of(info.unmap(), getId());
	}

	@Override
	protected String getExpectedType() {
		return GetInfoResultMessage.class.getName();
	}
}