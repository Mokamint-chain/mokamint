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
import io.mokamint.node.api.ChainInfo;
import io.mokamint.node.messages.GetChainInfoResultMessage;

/**
 * Implementation of the network message corresponding to the {@code PublicNode#getConfig()} method of a node.
 */
public class GetChainInfoResultMessageImpl extends AbstractRpcMessage implements GetChainInfoResultMessage {

	private final ChainInfo info;

	/**
	 * Creates the message.
	 * 
	 * @param info the chain information in the message
	 * @param id the identifier of the message
	 */
	public GetChainInfoResultMessageImpl(ChainInfo info, String id) {
		super(id);

		this.info = info;
	}

	@Override
	public ChainInfo get() {
		return info;
	}

	@Override
	public boolean equals(Object other) {
		return other instanceof GetChainInfoResultMessage && super.equals(other) && info.equals(((GetChainInfoResultMessage) other).get());
	}

	@Override
	protected String getExpectedType() {
		return GetChainInfoResultMessage.class.getName();
	}
}