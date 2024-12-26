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
import io.hotmoka.websockets.beans.api.InconsistentJsonException;
import io.mokamint.node.api.ChainPortion;
import io.mokamint.node.api.PublicNode;
import io.mokamint.node.messages.api.GetChainPortionResultMessage;
import io.mokamint.node.messages.internal.gson.GetChainPortionResultMessageJson;

/**
 * Implementation of the network message corresponding to the result of the {@link PublicNode#getChainPortion(long, int)} method.
 */
public class GetChainPortionResultMessageImpl extends AbstractRpcMessage implements GetChainPortionResultMessage {

	private final ChainPortion chain;

	/**
	 * Creates the message.
	 * 
	 * @param chain the chain hashes
	 * @param id the identifier of the message
	 */
	public GetChainPortionResultMessageImpl(ChainPortion chain, String id) {
		super(id);

		this.chain = Objects.requireNonNull(chain, "chain cannot be null");
	}

	/**
	 * Creates a message from the given JSON representation.
	 * 
	 * @param json the JSON representation
	 * @throws InconsistentJsonException if {@code json} is inconsistent
	 */
	public GetChainPortionResultMessageImpl(GetChainPortionResultMessageJson json) throws InconsistentJsonException {
		super(json.getId());

		var chain = json.getChain();
		if (chain == null)
			throw new InconsistentJsonException("chain cannot be null");

		this.chain = chain.unmap();
	}

	@Override
	public ChainPortion get() {
		return chain;
	}

	@Override
	public boolean equals(Object other) {
		return other instanceof GetChainPortionResultMessage gcrm && super.equals(other) && Objects.equals(get(), gcrm.get());
	}

	@Override
	protected String getExpectedType() {
		return GetChainPortionResultMessage.class.getName();
	}
}