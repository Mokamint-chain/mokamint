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
import java.util.Optional;

import io.hotmoka.websockets.beans.AbstractRpcMessage;
import io.hotmoka.websockets.beans.api.InconsistentJsonException;
import io.mokamint.node.api.MinerInfo;
import io.mokamint.node.api.RestrictedNode;
import io.mokamint.node.messages.api.OpenMinerResultMessage;
import io.mokamint.node.messages.internal.gson.OpenMinerResultMessageJson;

/**
 * Implementation of the network message corresponding to the info of the {@link RestrictedNode#openMiner(int)} method.
 */
public class OpenMinerResultMessageImpl extends AbstractRpcMessage implements OpenMinerResultMessage {

	/**
	 * The info of the call.
	 */
	private final Optional<MinerInfo> info;

	/**
	 * Creates the message.
	 * 
	 * @param info the info of the call
	 * @param id the identifier of the message
	 */
	public OpenMinerResultMessageImpl(Optional<MinerInfo> info, String id) {
		super(id);

		this.info = Objects.requireNonNull(info, "info cannot be null");
	}

	/**
	 * Creates a message from the given JSON representation.
	 * 
	 * @param json the JSON representation
	 * @throws InconsistentJsonException if {@code json} is inconsistent
	 */
	public OpenMinerResultMessageImpl(OpenMinerResultMessageJson json) throws InconsistentJsonException {
		super(json.getId());

		var info = json.getInfo();
		this.info = info.isPresent() ? Optional.of(info.get().unmap()) : Optional.empty();
	}

	@Override
	public boolean equals(Object other) {
		return other instanceof OpenMinerResultMessage omrm && super.equals(other) && Objects.equals(get(), omrm.get());
	}

	@Override
	protected String getExpectedType() {
		return OpenMinerResultMessage.class.getName();
	}

	@Override
	public Optional<MinerInfo> get() {
		return info;
	}
}