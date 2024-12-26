/*
Copyright 2024 Fausto Spoto

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

package io.mokamint.application.messages.internal;

import java.time.LocalDateTime;

import io.hotmoka.websockets.beans.AbstractRpcMessage;
import io.mokamint.application.api.Application;
import io.mokamint.application.messages.api.BeginBlockResultMessage;
import io.mokamint.application.messages.internal.gson.BeginBlockResultMessageJson;

/**
 * Implementation of the network message corresponding to the result of the {@link Application#beginBlock(long, byte[], LocalDateTime)} method.
 */
public class BeginBlockResultMessageImpl extends AbstractRpcMessage implements BeginBlockResultMessage {

	/**
	 * The result of the call.
	 */
	private final int result;

	/**
	 * Creates the message.
	 * 
	 * @param result the result of the call
	 * @param id the identifier of the message
	 */
	public BeginBlockResultMessageImpl(int result, String id) {
		super(id);

		this.result = result;
	}

	/**
	 * Creates a message from the given JSON representation.
	 * 
	 * @param json the JSON representation
	 */
	public BeginBlockResultMessageImpl(BeginBlockResultMessageJson json) {
		super(json.getId());

		this.result = json.getResult();
	}

	@Override
	public boolean equals(Object other) {
		return other instanceof BeginBlockResultMessage bbrm && super.equals(other) && result == bbrm.get().intValue();
	}

	@Override
	protected String getExpectedType() {
		return BeginBlockResultMessage.class.getName();
	}

	@Override
	public Integer get() {
		return result;
	}
}