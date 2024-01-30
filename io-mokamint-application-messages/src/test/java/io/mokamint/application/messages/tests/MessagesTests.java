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
import io.mokamint.application.messages.CheckTransactionMessages;
import io.mokamint.application.messages.CheckTransactionResultMessages;
import io.mokamint.application.messages.DeliverTransactionMessages;
import io.mokamint.application.messages.DeliverTransactionResultMessages;
import io.mokamint.application.messages.GetInitialStateIdMessages;
import io.mokamint.application.messages.GetInitialStateIdResultMessages;
import io.mokamint.application.messages.GetPriorityMessages;
import io.mokamint.application.messages.GetPriorityResultMessages;
import io.mokamint.application.messages.GetRepresentationMessages;
import io.mokamint.application.messages.GetRepresentationResultMessages;
import io.mokamint.node.Transactions;
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

	@Test
	@DisplayName("checkTransaction messages are correctly encoded into Json and decoded from Json")
	public void encodeDecodeWorksForCheckTransaction() throws EncodeException, DecodeException {
		var checkTransactionMessage1 = CheckTransactionMessages.of(Transactions.of(new byte[] { 13, 1, 19, 73 }), "id");
		String encoded = new CheckTransactionMessages.Encoder().encode(checkTransactionMessage1);
		var checkTransactionMessage2 = new CheckTransactionMessages.Decoder().decode(encoded);
		assertEquals(checkTransactionMessage1, checkTransactionMessage2);
	}

	@Test
	@DisplayName("checkTransaction result messages are correctly encoded into Json and decoded from Json")
	public void encodeDecodeWorksForCheckTransactionResult() throws EncodeException, DecodeException {
		var checkTransactionResultMessage1 = CheckTransactionResultMessages.of("id");
		String encoded = new CheckTransactionResultMessages.Encoder().encode(checkTransactionResultMessage1);
		var checkTransactionResultMessage2 = new CheckTransactionResultMessages.Decoder().decode(encoded);
		assertEquals(checkTransactionResultMessage1, checkTransactionResultMessage2);
	}

	@Test
	@DisplayName("deliverTransaction messages are correctly encoded into Json and decoded from Json")
	public void encodeDecodeWorksForDeliverTransaction() throws EncodeException, DecodeException {
		var deliverTransactionMessage1 = DeliverTransactionMessages.of(Transactions.of(new byte[] { 13, 1, 19, 73 }), 42, "id");
		String encoded = new DeliverTransactionMessages.Encoder().encode(deliverTransactionMessage1);
		var deliverTransactionMessage2 = new DeliverTransactionMessages.Decoder().decode(encoded);
		assertEquals(deliverTransactionMessage1, deliverTransactionMessage2);
	}

	@Test
	@DisplayName("deliverTransaction result messages are correctly encoded into Json and decoded from Json")
	public void encodeDecodeWorksForDeliverTransactionResult() throws EncodeException, DecodeException {
		var deliverTransactionResultMessage1 = DeliverTransactionResultMessages.of("id");
		String encoded = new DeliverTransactionResultMessages.Encoder().encode(deliverTransactionResultMessage1);
		var deliverTransactionResultMessage2 = new DeliverTransactionResultMessages.Decoder().decode(encoded);
		assertEquals(deliverTransactionResultMessage1, deliverTransactionResultMessage2);
	}

	@Test
	@DisplayName("getPriority messages are correctly encoded into Json and decoded from Json")
	public void encodeDecodeWorksForGetPriority() throws EncodeException, DecodeException {
		var getPriorityMessage1 = GetPriorityMessages.of(Transactions.of(new byte[] { 13, 1, 19, 73 }), "id");
		String encoded = new GetPriorityMessages.Encoder().encode(getPriorityMessage1);
		var getPriorityMessage2 = new GetPriorityMessages.Decoder().decode(encoded);
		assertEquals(getPriorityMessage1, getPriorityMessage2);
	}

	@Test
	@DisplayName("getPriority result messages are correctly encoded into Json and decoded from Json")
	public void encodeDecodeWorksForGetPriorityResult() throws EncodeException, DecodeException {
		var getPriorityResultMessage1 = GetPriorityResultMessages.of(1973L, "id");
		String encoded = new GetPriorityResultMessages.Encoder().encode(getPriorityResultMessage1);
		var getPriorityResultMessage2 = new GetPriorityResultMessages.Decoder().decode(encoded);
		assertEquals(getPriorityResultMessage1, getPriorityResultMessage2);
	}

	@Test
	@DisplayName("getRepresentation messages are correctly encoded into Json and decoded from Json")
	public void encodeDecodeWorksForGetRepresentation() throws EncodeException, DecodeException {
		var getRepresentationMessage1 = GetRepresentationMessages.of(Transactions.of(new byte[] { 13, 1, 19, 73 }), "id");
		String encoded = new GetRepresentationMessages.Encoder().encode(getRepresentationMessage1);
		var getRepresentationMessage2 = new GetRepresentationMessages.Decoder().decode(encoded);
		assertEquals(getRepresentationMessage1, getRepresentationMessage2);
	}

	@Test
	@DisplayName("getRepresentation result messages are correctly encoded into Json and decoded from Json")
	public void encodeDecodeWorksForGetRepresentationResult() throws EncodeException, DecodeException {
		var getRepresentationResultMessage1 = GetRepresentationResultMessages.of("h\"ello", "id");
		String encoded = new GetRepresentationResultMessages.Encoder().encode(getRepresentationResultMessage1);
		var getRepresentationResultMessage2 = new GetRepresentationResultMessages.Decoder().decode(encoded);
		assertEquals(getRepresentationResultMessage1, getRepresentationResultMessage2);
	}

	@Test
	@DisplayName("getInitialStateId messages are correctly encoded into Json and decoded from Json")
	public void encodeDecodeWorksForGetInitialStateId() throws EncodeException, DecodeException {
		var getInitialStateIdMessage1 = GetInitialStateIdMessages.of("id");
		String encoded = new GetInitialStateIdMessages.Encoder().encode(getInitialStateIdMessage1);
		var getInitialStateIdMessage2 = new GetInitialStateIdMessages.Decoder().decode(encoded);
		assertEquals(getInitialStateIdMessage1, getInitialStateIdMessage2);
	}

	@Test
	@DisplayName("getInitialStateId result messages are correctly encoded into Json and decoded from Json")
	public void encodeDecodeWorksForGetInitialStateIdResult() throws EncodeException, DecodeException {
		var getInitialStateIdResultMessage1 = GetInitialStateIdResultMessages.of(new byte[] { 1, 10, 100, 90, 87 }, "id");
		String encoded = new GetInitialStateIdResultMessages.Encoder().encode(getInitialStateIdResultMessage1);
		var getInitialStateIdResultMessage2 = new GetInitialStateIdResultMessages.Decoder().decode(encoded);
		assertEquals(getInitialStateIdResultMessage1, getInitialStateIdResultMessage2);
	}
}