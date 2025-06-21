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

package io.mokamint.miner.messages.internal;

import io.hotmoka.exceptions.ExceptionSupplier;
import io.hotmoka.exceptions.Objects;
import io.hotmoka.websockets.beans.AbstractRpcMessage;
import io.hotmoka.websockets.beans.api.InconsistentJsonException;
import io.mokamint.miner.messages.api.GetMiningSpecificationMessage;
import io.mokamint.miner.messages.internal.json.GetMiningSpecificationMessageJson;

/**
 * Implementation of the network message corresponding to {@link Node#addJarStoreInitialTransaction(JarStoreInitialTransactionRequest)}.
 */
public class GetMiningSpecificationMessageImpl extends AbstractRpcMessage implements GetMiningSpecificationMessage {

	/**
	 * Creates the message.
	 * 
	 * @param id the identifier of the message
	 */
	public GetMiningSpecificationMessageImpl(String id) {
		this(id, IllegalArgumentException::new);
	}

	/**
	 * Creates the message from the given JSON representation.
	 * 
	 * @param json the JSON representation
	 * @throws InconsistentJsonException if {@code json} is inconsistent
	 */
	public GetMiningSpecificationMessageImpl(GetMiningSpecificationMessageJson json) throws InconsistentJsonException {
		this(json.getId(), InconsistentJsonException::new);
	}

	/**
	 * Creates the message.
	 * 
	 * @param <E> the type of the exception thrown if some argument is illegal
	 * @param id the identifier of the message
	 * @param onIllegalArgs the creator of the exception thrown if some argument is illegal
	 * @throws E if some argument is illegal
	 */
	private <E extends Exception> GetMiningSpecificationMessageImpl(String id, ExceptionSupplier<? extends E> onIllegalArgs) throws E {
		super(Objects.requireNonNull(id, "id cannot be null", onIllegalArgs));
	}

	@Override
	public boolean equals(Object other) {
		return other instanceof GetMiningSpecificationMessage && super.equals(other);
	}

	@Override
	protected String getExpectedType() {
		return GetMiningSpecificationMessage.class.getName();
	}
}