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

import io.hotmoka.exceptions.ExceptionSupplierFromMessage;
import io.hotmoka.exceptions.Objects;
import io.hotmoka.websockets.beans.AbstractRpcMessage;
import io.hotmoka.websockets.beans.api.InconsistentJsonException;
import io.mokamint.application.api.Application;
import io.mokamint.application.messages.api.KeepFromMessage;
import io.mokamint.application.messages.internal.json.KeepFromMessageJson;

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
		this(start, id, IllegalArgumentException::new);
	}

	/**
	 * Creates a message from its JSON representation.
	 * 
	 * @param json the JSON representation
	 * @throws InconsistentJsonException if {@code json} is inconsistent
	 */
	public KeepFromMessageImpl(KeepFromMessageJson json) throws InconsistentJsonException {
		this(parse(Objects.requireNonNull(json.getStart(), "start cannot be null", InconsistentJsonException::new)), json.getId(), InconsistentJsonException::new);
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
	 * Creates the message.
	 * 
	 * @param <E> the type of the exception thrown if some argument is illegal
	 * @param start the limit time, before which states can be garbage-collected, present in the message
	 * @param id the identifier of the message
	 * @param onIllegalArgs the creator of the exception thrown if some argument is illegal
	 * @throws E if some argument is illegal
	 */
	private <E extends Exception> KeepFromMessageImpl(LocalDateTime start, String id, ExceptionSupplierFromMessage<? extends E> onIllegalArgs) throws E {
		super(id, onIllegalArgs);

		this.start = Objects.requireNonNull(start, "start cannot be null", onIllegalArgs);
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