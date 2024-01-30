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

import java.util.Arrays;

import io.hotmoka.websockets.beans.AbstractRpcMessage;
import io.mokamint.application.api.Application;
import io.mokamint.application.messages.api.GetInitialStateIdResultMessage;

/**
 * Implementation of the network message corresponding to the result of the {@link Application#getInitialStateId()} method.
 */
public class GetInitialStateIdResultMessageImpl extends AbstractRpcMessage implements GetInitialStateIdResultMessage {

	/**
	 * The result of the call.
	 */
	private final byte[] result;

	/**
	 * Creates the message.
	 * 
	 * @param result the result of the call
	 * @param id the identifier of the message
	 */
	public GetInitialStateIdResultMessageImpl(byte[] result, String id) {
		super(id);

		this.result = result.clone();
	}

	@Override
	public boolean equals(Object other) {
		return other instanceof GetInitialStateIdResultMessage gisirm && super.equals(other) && Arrays.equals(result, gisirm.get());
	}

	@Override
	protected String getExpectedType() {
		return GetInitialStateIdResultMessage.class.getName();
	}

	@Override
	public byte[] get() {
		return result.clone();
	}
}