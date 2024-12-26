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
import io.mokamint.node.messages.api.GetChainPortionMessage;
import io.mokamint.node.messages.internal.GetChainPortionMessageImpl;

/**
 * The JSON representation of a {@link GetChainPortionMessage}.
 */
public abstract class GetChainPortionMessageJson extends AbstractRpcMessageJsonRepresentation<GetChainPortionMessage> {
	private final long start;
	private final int count;

	protected GetChainPortionMessageJson(GetChainPortionMessage message) {
		super(message);

		this.start = message.getStart();
		this.count = message.getCount();
	}

	public long getStart() {
		return start;
	}

	public int getCount() {
		return count;
	}

	@Override
	public GetChainPortionMessage unmap() {
		return new GetChainPortionMessageImpl(this);
	}

	@Override
	protected String getExpectedType() {
		return GetChainPortionMessage.class.getName();
	}
}