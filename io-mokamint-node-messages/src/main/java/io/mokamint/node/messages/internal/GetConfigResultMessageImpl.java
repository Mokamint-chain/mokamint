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

import java.security.NoSuchAlgorithmException;
import java.util.Objects;

import io.hotmoka.websockets.beans.AbstractRpcMessage;
import io.hotmoka.websockets.beans.api.InconsistentJsonException;
import io.mokamint.node.api.ConsensusConfig;
import io.mokamint.node.api.PublicNode;
import io.mokamint.node.messages.api.GetConfigResultMessage;
import io.mokamint.node.messages.internal.json.GetConfigResultMessageJson;

/**
 * Implementation of the network message corresponding to the result of the {@link PublicNode#getConfig()} method.
 */
public class GetConfigResultMessageImpl extends AbstractRpcMessage implements GetConfigResultMessage {

	private final ConsensusConfig<?,?> config;

	/**
	 * Creates the message.
	 * 
	 * @param config the configuration in the message
	 * @param id the identifier of the message
	 */
	public GetConfigResultMessageImpl(ConsensusConfig<?,?> config, String id) {
		super(id);

		this.config = Objects.requireNonNull(config, "config cannot be null");
	}

	/**
	 * Creates a message from the given JSON representation.
	 * 
	 * @param json the JSON representation
	 * @throws NoSuchAlgorithmException if {@code json} refers to a non-available cryptographic algorithm
	 * @throws InconsistentJsonException if {@code json} is inconsistent
	 */
	public GetConfigResultMessageImpl(GetConfigResultMessageJson json) throws NoSuchAlgorithmException, InconsistentJsonException {
		super(json.getId());

		var config = json.getConfig();
		if (config == null)
			throw new InconsistentJsonException("config cannot be null");

		this.config = config.unmap();
	}

	@Override
	public ConsensusConfig<?,?> get() {
		return config;
	}

	@Override
	public boolean equals(Object other) {
		return other instanceof GetConfigResultMessage gcrm && super.equals(other) && config.equals(gcrm.get());
	}

	@Override
	protected String getExpectedType() {
		return GetConfigResultMessage.class.getName();
	}
}