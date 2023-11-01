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
import io.mokamint.node.api.PeerInfo;
import io.mokamint.node.api.RestrictedNode;
import io.mokamint.node.messages.api.AddPeerResultMessage;

/**
 * Implementation of the network message corresponding to the result of the {@link RestrictedNode#add(io.mokamint.node.api.Peer)} method.
 */
public class AddPeerResultMessageImpl extends AbstractRpcMessage implements AddPeerResultMessage {

	/**
	 * The result of the call.
	 */
	private final PeerInfo info;

	/**
	 * Creates the message.
	 * 
	 * @param info the result of the call
	 * @param id the identifier of the message
	 */
	public AddPeerResultMessageImpl(Optional<PeerInfo> info, String id) {
		super(id);

		Objects.requireNonNull(info, "info cannot be null");
		this.info = info.orElse(null);
	}

	@Override
	public boolean equals(Object other) {
		return other instanceof AddPeerResultMessage aprm && super.equals(other) && Objects.equals(get(), aprm.get());
	}

	@Override
	protected String getExpectedType() {
		return AddPeerResultMessage.class.getName();
	}

	@Override
	public Optional<PeerInfo> get() {
		return Optional.ofNullable(info);
	}
}