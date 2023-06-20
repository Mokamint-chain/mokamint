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
import io.mokamint.node.messages.VoidMessage;

/**
 * Implementation of a network message corresponding to the result of {@code void} method of a node.
 */
public class VoidMessageImpl extends AbstractRpcMessage implements VoidMessage {

	/**
	 * Creates the message.
	 * 
	 * @param id the identifier of the message
	 */
	public VoidMessageImpl(String id) {
		super(id);
	}

	@Override
	public boolean equals(Object other) {
		return other instanceof VoidMessage;
	}

	@Override
	protected String getExpectedType() {
		return VoidMessage.class.getName();
	}
}