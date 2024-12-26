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
import io.mokamint.node.messages.api.GetMempoolPortionMessage;
import io.mokamint.node.messages.internal.GetMempoolPortionMessageImpl;

/**
 * The JSON representation of a {@link GetMempoolPortionMessage}.
 */
public abstract class GetMempoolPortionMessageJson extends AbstractRpcMessageJsonRepresentation<GetMempoolPortionMessage> {
	private final int start;
	private final int count;

	protected GetMempoolPortionMessageJson(GetMempoolPortionMessage message) {
		super(message);

		this.start = message.getStart();
		this.count = message.getCount();
	}

	public int getStart() {
		return start;
	}

	public int getCount() {
		return count;
	}

	@Override
	public GetMempoolPortionMessage unmap() {
		return new GetMempoolPortionMessageImpl(this);
	}

	@Override
	protected String getExpectedType() {
		return GetMempoolPortionMessage.class.getName();
	}
}