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
 * The network message corresponding to the {@link Application#keepFrom(java.time.LocalDateTime)} method.
 */
@Immutable
public interface KeepFromMessage extends RpcMessage {

	/**
	 * Yields the the limit time, before which states can be garbage-collected, present in the message.
	 * 
	 * @return the limit time
	 */
	LocalDateTime getStart();
}