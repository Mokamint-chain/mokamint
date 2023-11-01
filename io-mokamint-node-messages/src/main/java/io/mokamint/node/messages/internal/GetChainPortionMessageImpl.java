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
 * Implementation of the network message corresponding to the {@link PublicNode#getChainPortion(long, int)} method.
 */
public class GetChainPortionMessageImpl extends AbstractRpcMessage implements GetChainPortionMessage {
	private final long start;
	private final int count;

	/**
	 * Creates the message.
	 * 
	 * @param start the {@code start} parameter of the method
	 * @param count the {@code count} parameter of the method
	 * @param id the identifier of the message
	 */
	public GetChainPortionMessageImpl(long start, int count, String id) {
		super(id);

		this.start = start;
		this.count = count;
	}

	@Override
	public long getStart() {
		return start;
	}

	@Override
	public int getCount() {
		return count;
	}

	@Override
	public boolean equals(Object other) {
		return other instanceof GetChainPortionMessage gcpm && super.equals(other) && start == gcpm.getStart() && count == gcpm.getCount();
	}

	@Override
	protected String getExpectedType() {
		return GetChainPortionMessage.class.getName();
	}
}