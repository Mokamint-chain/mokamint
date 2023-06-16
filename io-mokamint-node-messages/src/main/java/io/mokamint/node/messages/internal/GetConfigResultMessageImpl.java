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
import io.mokamint.node.api.ConsensusConfig;
import io.mokamint.node.messages.GetConfigResultMessage;

/**
 * Implementation of the network message corresponding to the {@code getConfig} method of a node.
 */
public class GetConfigResultMessageImpl extends AbstractRpcMessage implements GetConfigResultMessage {

	private final ConsensusConfig config;

	/**
	 * Creates the message.
	 * 
	 * @param config the configuration in the message
	 * @param id the identifier of the message
	 */
	public GetConfigResultMessageImpl(ConsensusConfig config, String id) {
		super(id);

		this.config = config;
	}

	@Override
	public ConsensusConfig get() {
		return config;
	}

	@Override
	public boolean equals(Object other) {
		return other instanceof GetConfigResultMessage && config.equals(((GetConfigResultMessage) other).get());
	}

	@Override
	protected String getExpectedType() {
		return GetConfigResultMessage.class.getName();
	}
}