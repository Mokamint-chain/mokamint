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
import io.mokamint.application.messages.api.EndBlockMessage;
import io.mokamint.application.messages.internal.json.EndBlockMessageJson;
import io.mokamint.nonce.api.Deadline;

/**
 * Implementation of the network message corresponding to {@link Application#endBlock(int, Deadline)}.
 */
public class EndBlockMessageImpl extends AbstractRpcMessage implements EndBlockMessage {
	private final int groupId;
	private final Deadline deadline;

	/**
	 * Creates the message.
	 * 
	 * @param groupId the identifier of the group of transactions in the message
	 * @param deadline the deadline in the message
	 * @param id the identifier of the message
	 */
	public EndBlockMessageImpl(int groupId, Deadline deadline, String id) {
		this(groupId, deadline, id, IllegalArgumentException::new);
	}

	/**
	 * Creates a message from the given JSON representation.
	 * 
	 * @param json the JSON representation
	 * @throws InconsistentJsonException if the JSON representation is inconsistent
	 * @throws NoSuchAlgorithmException if the message refers to a non-available cryptographic algorithm
	 */
	public EndBlockMessageImpl(EndBlockMessageJson json) throws InconsistentJsonException, NoSuchAlgorithmException {
		this(
			json.getGroupId(),
			Objects.requireNonNull(json.getDeadline(), "deadline cannot be null", InconsistentJsonException::new).unmap(),
			json.getId(),
			InconsistentJsonException::new
		);
	}

	/**
	 * Creates the message.
	 * 
	 * @param <E> the type of the exception thrown if some argument is illegal
	 * @param groupId the identifier of the group of transactions in the message
	 * @param deadline the deadline in the message
	 * @param id the identifier of the message
	 * @param onIllegalArgs the creator of the exception thrown if some argument is illegal
	 * @throws E if some argument is illegal
	 */
	private <E extends Exception> EndBlockMessageImpl(int groupId, Deadline deadline, String id, ExceptionSupplierFromMessage<? extends E> onIllegalArgs) throws E {
		super(id, onIllegalArgs);

		this.groupId = groupId;
		this.deadline = Objects.requireNonNull(deadline, "deadline cannot be null", onIllegalArgs);
	}

	@Override
	public Deadline getDeadline() {
		return deadline;
	}

	@Override
	public int getGroupId() {
		return groupId;
	}

	@Override
	public boolean equals(Object other) {
		return other instanceof EndBlockMessage ebm && super.equals(other)
			&& groupId == ebm.getGroupId()
			&& deadline.equals(ebm.getDeadline());
	}

	@Override
	protected String getExpectedType() {
		return EndBlockMessage.class.getName();
	}
}