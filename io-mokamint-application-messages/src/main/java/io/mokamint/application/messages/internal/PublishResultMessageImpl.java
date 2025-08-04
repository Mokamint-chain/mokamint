/*
Copyright 2025 Fausto Spoto

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

import io.hotmoka.exceptions.ExceptionSupplierFromMessage;
import io.hotmoka.websockets.beans.AbstractVoidResultMessage;
import io.hotmoka.websockets.beans.api.InconsistentJsonException;
import io.mokamint.application.api.Application;
import io.mokamint.application.messages.api.PublishResultMessage;
import io.mokamint.application.messages.internal.json.PublishResultMessageJson;

/**
 * Implementation of the network message corresponding to the result of the
 * {@link Application#publish(io.mokamint.node.api.Block)} method.
 */
public class PublishResultMessageImpl extends AbstractVoidResultMessage implements PublishResultMessage {

	/**
	 * Creates the message.
	 * 
	 * @param id the identifier of the message
	 */
	public PublishResultMessageImpl(String id) {
		this(id, IllegalArgumentException::new);
	}

	/**
	 * Creates a message from its JSON representation.
	 * 
	 * @param json the JSON representation
	 * @throws InconsistentJsonException if {@code json} is inconsistent
	 */
	public PublishResultMessageImpl(PublishResultMessageJson json) throws InconsistentJsonException {
		this(json.getId(), InconsistentJsonException::new);
	}

	/**
	 * Creates a message from the given JSON representation.
	 * 
	 * @param <E> the exception to throw if some argument is illegal
	 * @param id the identifier of the message
	 * @param onIllegalArgs the provider of the exception to throw if some argument is illegal
	 * @throws E if some argument is illegal
	 */
	private <E extends Exception> PublishResultMessageImpl(String id, ExceptionSupplierFromMessage<? extends E> onIllegalArgs) throws E {
		super(id, onIllegalArgs);
	}

	@Override
	public boolean equals(Object other) {
		return other instanceof PublishResultMessage && super.equals(other);
	}

	@Override
	protected String getExpectedType() {
		return PublishResultMessage.class.getName();
	}
}