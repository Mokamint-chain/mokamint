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

import static java.time.format.DateTimeFormatter.ISO_LOCAL_DATE_TIME;

import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.util.Arrays;

import io.hotmoka.crypto.Hex;
import io.hotmoka.exceptions.ExceptionSupplierFromMessage;
import io.hotmoka.exceptions.Objects;
import io.hotmoka.websockets.beans.AbstractRpcMessage;
import io.hotmoka.websockets.beans.api.InconsistentJsonException;
import io.mokamint.application.api.Application;
import io.mokamint.application.messages.api.BeginBlockMessage;
import io.mokamint.application.messages.internal.json.BeginBlockMessageJson;

/**
 * Implementation of the network message corresponding to {@link Application#beginBlock(long, byte[], LocalDateTime)}.
 */
public class BeginBlockMessageImpl extends AbstractRpcMessage implements BeginBlockMessage {
	private final long height;
	private final byte[] stateId;
	private final LocalDateTime when;

	/**
	 * Creates the message.
	 * 
	 * @param height the block height in the message
	 * @param when the block creation time in the message
	 * @param stateId the state identifier in the message
	 * @param id the identifier of the message
	 */
	public BeginBlockMessageImpl(long height, LocalDateTime when, byte[] stateId, String id) {
		this(height, when, stateId, id, IllegalArgumentException::new);
	}

	/**
	 * Creates a message from the given JSON representation.
	 * 
	 * @param json the JSON representation
	 * @throws InconsistentJsonException if {@code json} is inconsistent
	 */
	public BeginBlockMessageImpl(BeginBlockMessageJson json) throws InconsistentJsonException {
		this(
			json.getHeight(),
			parse(Objects.requireNonNull(json.getWhen(), "when cannot be null", InconsistentJsonException::new)),
			Hex.fromHexString(Objects.requireNonNull(json.getStateId(), "stateId cannot be null", InconsistentJsonException::new), InconsistentJsonException::new),
			json.getId(),
			InconsistentJsonException::new
		);
	}

	private static LocalDateTime parse(String when) throws InconsistentJsonException {
		try {
			return LocalDateTime.parse(when, ISO_LOCAL_DATE_TIME);
		}
		catch (DateTimeParseException e) {
			throw new InconsistentJsonException(e);
		}
	}

	/**
	 * Creates a message from the given JSON representation.
	 * 
	 * @param <E> the exception to throw if some argument is illegal
	 * @param height the block height in the message
	 * @param when the block creation time in the message
	 * @param stateId the state identifier in the message
	 * @param id the identifier of the message
	 * @param onIllegalArgs the provider of the exception to throw if some argument is illegal
	 * @throws E if some argument is illegal
	 */
	private <E extends Exception> BeginBlockMessageImpl(long height, LocalDateTime when, byte[] stateId, String id, ExceptionSupplierFromMessage<? extends E> onIllegalArgs) throws E {
		super(id, onIllegalArgs);

		this.height = height;
		if (height < 0)
			throw onIllegalArgs.apply("height must be non-negative");

		this.stateId = Objects.requireNonNull(stateId, "stateId cannot be null", onIllegalArgs).clone();
		this.when = Objects.requireNonNull(when, "when cannot be null", onIllegalArgs);
	}

	@Override
	public long getHeight() {
		return height;
	}

	@Override
	public byte[] getStateId() {
		return stateId.clone();
	}

	@Override
	public LocalDateTime getWhen() {
		return when;
	}

	@Override
	public boolean equals(Object other) {
		return other instanceof BeginBlockMessage bbm && super.equals(other)
			&& height == bbm.getHeight()
			&& when.equals(bbm.getWhen())
			&& Arrays.equals(stateId, bbm.getStateId());
	}

	@Override
	protected String getExpectedType() {
		return BeginBlockMessage.class.getName();
	}
}