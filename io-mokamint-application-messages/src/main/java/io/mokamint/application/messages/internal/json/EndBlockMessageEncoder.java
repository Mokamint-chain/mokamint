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

package io.mokamint.application.messages.internal.json;

import io.hotmoka.websockets.beans.MappedEncoder;
import io.mokamint.application.messages.EndBlockMessages;
import io.mokamint.application.messages.api.EndBlockMessage;

/**
 * An encoder of a {@code EndBlockMessage}.
 */
public class EndBlockMessageEncoder extends MappedEncoder<EndBlockMessage, EndBlockMessages.Json> {

	public EndBlockMessageEncoder() {
		super(EndBlockMessages.Json::new);
	}
}