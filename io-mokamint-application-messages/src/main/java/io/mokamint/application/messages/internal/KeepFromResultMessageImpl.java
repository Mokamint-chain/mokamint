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

import io.hotmoka.websockets.beans.AbstractVoidResultMessage;
import io.mokamint.application.api.Application;
import io.mokamint.application.messages.api.KeepFromResultMessage;
import io.mokamint.application.messages.internal.gson.KeepFromResultMessageJson;

/**
 * Implementation of the network message corresponding to the result of the
 * {@link Application#keepFrom(java.time.LocalDateTime)} method.
 */
public class KeepFromResultMessageImpl extends AbstractVoidResultMessage implements KeepFromResultMessage {

	/**
	 * Creates the message.
	 * 
	 * @param id the identifier of the message
	 */
	public KeepFromResultMessageImpl(String id) {
		super(id);
	}

	/**
	 * Creates a message from its JSON representation.
	 * 
	 * @param json the JSON representation
	 */
	public KeepFromResultMessageImpl(KeepFromResultMessageJson json) {
		super(json.getId());
	}

	@Override
	public boolean equals(Object other) {
		return other instanceof KeepFromResultMessage && super.equals(other);
	}

	@Override
	protected String getExpectedType() {
		return KeepFromResultMessage.class.getName();
	}
}