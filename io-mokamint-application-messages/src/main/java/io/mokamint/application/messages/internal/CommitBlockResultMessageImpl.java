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
import io.mokamint.application.messages.api.CommitBlockResultMessage;

/**
 * Implementation of the network message corresponding to the result of the
 * {@link Application#commitBlock(int)} method.
 */
public class CommitBlockResultMessageImpl extends AbstractVoidResultMessage implements CommitBlockResultMessage {

	/**
	 * Creates the message.
	 * 
	 * @param id the identifier of the message
	 */
	public CommitBlockResultMessageImpl(String id) {
		super(id);
	}

	@Override
	public boolean equals(Object other) {
		return other instanceof CommitBlockResultMessage && super.equals(other);
	}

	@Override
	protected String getExpectedType() {
		return CommitBlockResultMessage.class.getName();
	}
}