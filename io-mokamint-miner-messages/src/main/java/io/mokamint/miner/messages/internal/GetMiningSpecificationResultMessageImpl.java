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

import java.security.NoSuchAlgorithmException;

import io.hotmoka.exceptions.ExceptionSupplier;
import io.hotmoka.exceptions.Objects;
import io.hotmoka.websockets.beans.AbstractRpcMessage;
import io.hotmoka.websockets.beans.api.InconsistentJsonException;
import io.mokamint.miner.api.Miner;
import io.mokamint.miner.api.MiningSpecification;
import io.mokamint.miner.messages.api.GetMiningSpecificationResultMessage;
import io.mokamint.miner.messages.internal.json.GetMiningSpecificationResultMessageJson;

/**
 * Implementation of the network message corresponding to the result of the {@link Miner#getMiningSpecification()} method.
 */
public class GetMiningSpecificationResultMessageImpl extends AbstractRpcMessage implements GetMiningSpecificationResultMessage {

	/**
	 * The result of the call.
	 */
	private final MiningSpecification result;

	/**
	 * Creates the message.
	 * 
	 * @param result the result of the call
	 * @param id the identifier of the message
	 */
	public GetMiningSpecificationResultMessageImpl(MiningSpecification result, String id) {
		this(result, id, IllegalArgumentException::new);
	}

	/**
	 * Creates the message from the given JSON representation.
	 * 
	 * @param json the JSON representation
	 * @throws InconsistentJsonException if {@code json} is inconsistent
	 * @throws NoSuchAlgorithmException if {@code json} refers to a non-available cryptographic algorithm
	 */
	public GetMiningSpecificationResultMessageImpl(GetMiningSpecificationResultMessageJson json) throws InconsistentJsonException, NoSuchAlgorithmException {
		this(
			Objects.requireNonNull(json.getResult(), "result cannot be null", InconsistentJsonException::new).unmap(),
			json.getId(),
			InconsistentJsonException::new
		);
	}

	/**
	 * Creates the message.
	 * 
	 * @param <E> the type of the exception thrown if some argument is illegal
	 * @param result the result of the transaction
	 * @param id the identifier of the message
	 * @param onIllegalArgs the creator of the exception thrown if some argument is illegal
	 * @throws E if some argument is illegal
	 */
	private <E extends Exception> GetMiningSpecificationResultMessageImpl(MiningSpecification result, String id, ExceptionSupplier<? extends E> onIllegalArgs) throws E {
		super(Objects.requireNonNull(id, "id cannot be null", onIllegalArgs));
	
		this.result = Objects.requireNonNull(result, "result cannot be null", onIllegalArgs);
	}

	@Override
	public boolean equals(Object other) {
		return other instanceof GetMiningSpecificationResultMessage gmsrm && super.equals(other) && result.equals(gmsrm.get());
	}

	@Override
	protected String getExpectedType() {
		return GetMiningSpecificationResultMessage.class.getName();
	}

	@Override
	public MiningSpecification get() {
		return result;
	}
}