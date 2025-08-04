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

import io.hotmoka.exceptions.ExceptionSupplierFromMessage;
import io.hotmoka.websockets.beans.AbstractRpcMessage;
import io.hotmoka.websockets.beans.api.InconsistentJsonException;
import io.mokamint.application.api.Application;
import io.mokamint.application.messages.api.CommitBlockMessage;
import io.mokamint.application.messages.internal.json.CommitBlockMessageJson;

/**
 * Implementation of the network message corresponding to {@link Application#commitBlock(int)}.
 */
public class CommitBlockMessageImpl extends AbstractRpcMessage implements CommitBlockMessage {
	private final int groupId;

	/**
	 * Creates the message.
	 * 
	 * @param groupId the identifier of the group of transactions in the message
	 * @param id the identifier of the message
	 */
	public CommitBlockMessageImpl(int groupId, String id) {
		this(groupId, id, IllegalArgumentException::new);
	}

	/**
	 * Creates a message from the given JSON representation.
	 * 
	 * @param json the JSON representation
	 * @throws InconsistentJsonException if {@code json} is inconsistent
	 */
	public CommitBlockMessageImpl(CommitBlockMessageJson json) throws InconsistentJsonException {
		this(json.getGroupId(), json.getId(), InconsistentJsonException::new);
	}

	/**
	 * Creates a message from the given JSON representation.
	 * 
	 * @param <E> the exception to throw if some argument is illegal
	 * @param groupId the identifier of the group of transactions in the message
	 * @param id the identifier of the message
	 * @param onIllegalArgs the provider of the exception to throw if some argument is illegal
	 * @throws E if some argument is illegal
	 */
	private <E extends Exception> CommitBlockMessageImpl(int groupId, String id, ExceptionSupplierFromMessage<? extends E> onIllegalArgs) throws E {
		super(id, onIllegalArgs);

		this.groupId = groupId;
	}

	@Override
	public int getGroupId() {
		return groupId;
	}

	@Override
	public boolean equals(Object other) {
		return other instanceof CommitBlockMessage cbm && super.equals(other) && groupId == cbm.getGroupId();
	}

	@Override
	protected String getExpectedType() {
		return CommitBlockMessage.class.getName();
	}
}