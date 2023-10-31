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

package io.mokamint.node.messages.internal;

import io.hotmoka.websockets.beans.AbstractRpcMessage;
import io.mokamint.node.api.PublicNode;
import io.mokamint.node.messages.api.GetChainPortionMessage;

/**
 * Implementation of the network message corresponding to the {@link PublicNode#getChainPortion(long, long)} method.
 */
public class GetChainPortionMessageImpl extends AbstractRpcMessage implements GetChainPortionMessage {
	private final long start;
	private final long count;

	/**
	 * Creates the message.
	 * 
	 * @param start the {@code start} parameter of the method
	 * @param count the {@code count} parameter of the method
	 * @param id the identifier of the message
	 */
	public GetChainPortionMessageImpl(long start, long count, String id) {
		super(id);

		this.start = start;
		this.count = count;
	}

	@Override
	public long getStart() {
		return start;
	}

	@Override
	public long getCount() {
		return count;
	}

	@Override
	public boolean equals(Object other) {
		return other instanceof GetChainPortionMessage gcm && super.equals(other) && start == gcm.getStart() && count == gcm.getCount();
	}

	@Override
	protected String getExpectedType() {
		return GetChainPortionMessage.class.getName();
	}
}