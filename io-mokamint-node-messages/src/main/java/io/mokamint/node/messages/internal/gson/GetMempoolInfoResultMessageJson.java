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
import io.hotmoka.websockets.beans.api.InconsistentJsonException;
import io.mokamint.node.MempoolInfos;
import io.mokamint.node.messages.api.GetMempoolInfoResultMessage;
import io.mokamint.node.messages.internal.GetMempoolInfoResultMessageImpl;

/**
 * The JSON representation of a {@link GetMempoolInfoResultMessage}.
 */
public abstract class GetMempoolInfoResultMessageJson extends AbstractRpcMessageJsonRepresentation<GetMempoolInfoResultMessage> {
	private final MempoolInfos.Json info;

	protected GetMempoolInfoResultMessageJson(GetMempoolInfoResultMessage message) {
		super(message);

		this.info = new MempoolInfos.Json(message.get());
	}

	public MempoolInfos.Json getInfo() {
		return info;
	}

	@Override
	public GetMempoolInfoResultMessage unmap() throws InconsistentJsonException {
		return new GetMempoolInfoResultMessageImpl(this);
	}

	@Override
	protected String getExpectedType() {
		return GetMempoolInfoResultMessage.class.getName();
	}
}