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
import io.mokamint.application.messages.api.KeepFromMessage;

/**
 * Implementation of the network message corresponding to {@link Application#keepFrom(java.time.LocalDateTime)}.
 */
public class KeepFromMessageImpl extends AbstractRpcMessage implements KeepFromMessage {
	private final LocalDateTime start;

	/**
	 * Creates the message.
	 * 
	 * @param start the limit time, before which states can be garbage-collected, present in the message
	 * @param id the identifier of the message
	 */
	public KeepFromMessageImpl(LocalDateTime start, String id) {
		super(id);

		this.start = start;
	}

	@Override
	public LocalDateTime getStart() {
		return start;
	}

	@Override
	public boolean equals(Object other) {
		return other instanceof KeepFromMessage kfm && super.equals(other) && start.equals(kfm.getStart());
	}

	@Override
	protected String getExpectedType() {
		return KeepFromMessage.class.getName();
	}
}