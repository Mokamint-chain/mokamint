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

import io.hotmoka.crypto.Base64;
import io.hotmoka.exceptions.ExceptionSupplierFromMessage;
import io.hotmoka.exceptions.Objects;
import io.hotmoka.websockets.beans.AbstractRpcMessage;
import io.hotmoka.websockets.beans.api.InconsistentJsonException;
import io.mokamint.application.api.Application;
import io.mokamint.application.messages.api.GetPriorityMessage;
import io.mokamint.application.messages.internal.json.GetPriorityMessageJson;
import io.mokamint.node.Requests;
import io.mokamint.node.api.Request;

/**
 * Implementation of the network message corresponding to {@link Application#getPriority(Request)}.
 */
public class GetPriorityMessageImpl extends AbstractRpcMessage implements GetPriorityMessage {
	private final Request request;

	/**
	 * Creates the message.
	 * 
	 * @param request the request in the message
	 * @param id the identifier of the message
	 */
	public GetPriorityMessageImpl(Request request, String id) {
		this(request, id, IllegalArgumentException::new);
	}

	/**
	 * Creates a message from the given JSON representation.
	 * 
	 * @param json the JSON representation
	 * @throws InconsistentJsonException if the JSON representation is inconsistent
	 */
	public GetPriorityMessageImpl(GetPriorityMessageJson json) throws InconsistentJsonException {
		this(
			Requests.of(Base64.fromBase64String(Objects.requireNonNull(json.getTransaction(), "request cannot be null", InconsistentJsonException::new), InconsistentJsonException::new)),
			json.getId(),
			InconsistentJsonException::new
		);
	}

	/**
	 * Creates the message.
	 * 
	 * @param <E> the type of the exception thrown if some argument is illegal
	 * @param request the request in the message
	 * @param id the identifier of the message
	 * @param onIllegalArgs the creator of the exception thrown if some argument is illegal
	 * @throws E if some argument is illegal
	 */
	private <E extends Exception> GetPriorityMessageImpl(Request request, String id, ExceptionSupplierFromMessage<? extends E> onIllegalArgs) throws E {
		super(id, onIllegalArgs);

		this.request = Objects.requireNonNull(request, "request cannot be null", onIllegalArgs);
	}

	@Override
	public Request getTransaction() {
		return request;
	}

	@Override
	public boolean equals(Object other) {
		return other instanceof GetPriorityMessage cpm && super.equals(other) && request.equals(cpm.getTransaction());
	}

	@Override
	protected String getExpectedType() {
		return GetPriorityMessage.class.getName();
	}
}