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

import java.security.NoSuchAlgorithmException;

import io.hotmoka.exceptions.ExceptionSupplierFromMessage;
import io.hotmoka.exceptions.Objects;
import io.hotmoka.websockets.beans.AbstractRpcMessage;
import io.hotmoka.websockets.beans.api.InconsistentJsonException;
import io.mokamint.application.api.Application;
import io.mokamint.application.messages.api.CheckDeadlineMessage;
import io.mokamint.application.messages.internal.json.CheckDeadlineMessageJson;
import io.mokamint.nonce.api.Deadline;

/**
 * Implementation of the network message corresponding to {@link Application#checkDeadline(byte[])}.
 */
public class CheckDeadlineMessageImpl extends AbstractRpcMessage implements CheckDeadlineMessage {
	private final Deadline deadline;

	/**
	 * Creates the message.
	 * 
	 * @param deadline the deadline to check
	 * @param id the identifier of the message
	 */
	public CheckDeadlineMessageImpl(Deadline deadline, String id) {
		this(deadline, id, IllegalArgumentException::new);
	}

	/**
	 * Creates a message from the given JSON representation.
	 * 
	 * @param json the JSON representation
	 * @throws InconsistentJsonException if {@code json} is inconsistent
	 * @throws NoSuchAlgorithmException if the JSON refers to a missing cryptographic algorithm
	 */
	public CheckDeadlineMessageImpl(CheckDeadlineMessageJson json) throws InconsistentJsonException, NoSuchAlgorithmException {
		this(
			Objects.requireNonNull(json.getDeadline(), "deadline cannot be null", InconsistentJsonException::new).unmap(),
			json.getId(),
			InconsistentJsonException::new
		);
	}

	/**
	 * Creates a message from the given JSON representation.
	 * 
	 * @param <E> the exception to throw if some argument is illegal
	 * @param deadline the deadline to check
	 * @param id the identifier of the message
	 * @param onIllegalArgs the provider of the exception to throw if some argument is illegal
	 * @throws E if some argument is illegal
	 */
	private <E extends Exception> CheckDeadlineMessageImpl(Deadline deadline, String id, ExceptionSupplierFromMessage<? extends E> onIllegalArgs) throws E {
		super(id, onIllegalArgs);

		this.deadline = Objects.requireNonNull(deadline, "deadline cannot be null", onIllegalArgs);
	}

	@Override
	public Deadline getDeadline() {
		return deadline;
	}

	@Override
	public boolean equals(Object other) {
		return other instanceof CheckDeadlineMessage cdm && super.equals(other) && deadline.equals(cdm.getDeadline());
	}

	@Override
	protected String getExpectedType() {
		return CheckDeadlineMessage.class.getName();
	}
}