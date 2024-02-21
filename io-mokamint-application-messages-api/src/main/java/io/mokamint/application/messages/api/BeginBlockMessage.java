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

package io.mokamint.application.messages.api;

import java.time.LocalDateTime;

import io.hotmoka.annotations.Immutable;
import io.hotmoka.websockets.beans.api.RpcMessage;
import io.mokamint.application.api.Application;

/**
 * The network message corresponding to the {@link Application#beginBlock(long, LocalDateTime, byte[])} method.
 */
@Immutable
public interface BeginBlockMessage extends RpcMessage {

	/**
	 * Yields the block height in the message.
	 * 
	 * @return the block height
	 */
	long getHeight();

	/**
	 * Yields the state identifier in the message.
	 * 
	 * @return the state identifier
	 */
	byte[] getStateId();

	/**
	 * Yields the time in the message.
	 * 
	 * @return the time
	 */
	LocalDateTime getWhen();
}