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

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Objects;

import io.hotmoka.websockets.beans.AbstractRpcMessage;
import io.mokamint.application.api.Application;
import io.mokamint.application.messages.api.BeginBlockMessage;

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
	 * @param stateId the state identifier in the message
	 * @param when the block creation time in the message
	 * @param id the identifier of the message
	 */
	public BeginBlockMessageImpl(long height, byte[] stateId, LocalDateTime when, String id) {
		super(id);

		this.height = height;
		if (height < 0)
			throw new IllegalArgumentException("height must be non-negative");

		this.stateId = stateId.clone();
		this.when = Objects.requireNonNull(when, "when cannot be null");
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