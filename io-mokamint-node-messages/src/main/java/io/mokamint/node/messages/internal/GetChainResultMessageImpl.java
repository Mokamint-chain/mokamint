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

import java.util.Objects;

import io.hotmoka.websockets.beans.AbstractRpcMessage;
import io.mokamint.node.api.Chain;
import io.mokamint.node.api.PublicNode;
import io.mokamint.node.messages.api.GetChainResultMessage;

/**
 * Implementation of the network message corresponding to the result of the {@link PublicNode#getChain(long, long)} method.
 */
public class GetChainResultMessageImpl extends AbstractRpcMessage implements GetChainResultMessage {

	private final Chain chain;

	/**
	 * Creates the message.
	 * 
	 * @param chain the chain hashes
	 * @param id the identifier of the message
	 */
	public GetChainResultMessageImpl(Chain chain, String id) {
		super(id);

		Objects.requireNonNull(chain, "chain cannot be null");
		this.chain = chain;
	}

	@Override
	public Chain get() {
		return chain;
	}

	@Override
	public boolean equals(Object other) {
		return other instanceof GetChainResultMessage gcrm && super.equals(other) && Objects.equals(get(), gcrm.get());
	}

	@Override
	protected String getExpectedType() {
		return GetChainResultMessage.class.getName();
	}
}