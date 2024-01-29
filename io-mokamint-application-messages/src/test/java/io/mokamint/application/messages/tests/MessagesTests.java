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

package io.mokamint.application.messages.tests;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import io.hotmoka.testing.AbstractLoggedTests;
import io.mokamint.application.messages.CheckPrologExtraMessages;
import io.mokamint.application.messages.CheckPrologExtraResultMessages;
import jakarta.websocket.DecodeException;
import jakarta.websocket.EncodeException;

public class MessagesTests extends AbstractLoggedTests {

	@Test
	@DisplayName("checkPrologExtra messages are correctly encoded into Json and decoded from Json")
	public void encodeDecodeWorksForCheckPrologExtra() throws EncodeException, DecodeException {
		var checkPrologExtraMessage1 = CheckPrologExtraMessages.of(new byte[] { 13, 1, 19, 73 }, "id");
		String encoded = new CheckPrologExtraMessages.Encoder().encode(checkPrologExtraMessage1);
		var checkPrologExtraMessage2 = new CheckPrologExtraMessages.Decoder().decode(encoded);
		assertEquals(checkPrologExtraMessage1, checkPrologExtraMessage2);
	}

	@Test
	@DisplayName("checkPrologExtraResult messages are correctly encoded into Json and decoded from Json")
	public void encodeDecodeWorksForCheckPrologExtraResult() throws EncodeException, DecodeException {
		var checkPrologExtraResultMessage1 = CheckPrologExtraResultMessages.of(true, "id");
		String encoded = new CheckPrologExtraResultMessages.Encoder().encode(checkPrologExtraResultMessage1);
		var checkPrologExtraResultMessage2 = new CheckPrologExtraResultMessages.Decoder().decode(encoded);
		assertEquals(checkPrologExtraResultMessage1, checkPrologExtraResultMessage2);
	}
}