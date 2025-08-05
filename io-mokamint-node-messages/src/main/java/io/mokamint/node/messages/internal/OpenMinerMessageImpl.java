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
import io.hotmoka.websockets.beans.api.InconsistentJsonException;
import io.mokamint.node.api.RestrictedNode;
import io.mokamint.node.messages.api.OpenMinerMessage;
import io.mokamint.node.messages.internal.json.OpenMinerMessageJson;

/**
 * Implementation of the network message corresponding to {@link RestrictedNode#openMiner(int)}.
 */
public class OpenMinerMessageImpl extends AbstractRpcMessage implements OpenMinerMessage {

	private final int port;

	/**
	 * Creates the message.
	 * 
	 * @param port the port required for the miner
	 * @param id the identifier of the message
	 */
	public OpenMinerMessageImpl(int port, String id) {
		super(id);

		if (port < 0 || port > 65535)
			throw new IllegalArgumentException("The port number " + port + " is illegal: it must be between 0 and 65535 inclusive");

		this.port = port;
	}

	/**
	 * Creates a message from the given JSON representation.
	 * 
	 * @param json the JSON representation
	 * @throws InconsistentJsonException if {@code json} is inconsistent
	 */
	public OpenMinerMessageImpl(OpenMinerMessageJson json) throws InconsistentJsonException {
		super(json.getId());

		var port = json.getPort();
		if (port < 0 || port > 65535)
			throw new InconsistentJsonException("The port number " + port + " is illegal: it must be between 0 and 65535 inclusive");

		this.port = port;
	}

	@Override
	public int getPort() {
		return port;
	}

	@Override
	public boolean equals(Object other) {
		return other instanceof OpenMinerMessage omm && super.equals(other) && port == omm.getPort();
	}

	@Override
	protected String getExpectedType() {
		return OpenMinerMessage.class.getName();
	}
}