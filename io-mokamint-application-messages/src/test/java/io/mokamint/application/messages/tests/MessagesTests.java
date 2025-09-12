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

import java.math.BigInteger;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Optional;
import java.util.Random;
import java.util.stream.Stream;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import io.hotmoka.crypto.HashingAlgorithms;
import io.hotmoka.crypto.SignatureAlgorithms;
import io.hotmoka.testing.AbstractLoggedTests;
import io.mokamint.application.Infos;
import io.mokamint.application.messages.AbortBlockMessages;
import io.mokamint.application.messages.AbortBlockResultMessages;
import io.mokamint.application.messages.BeginBlockMessages;
import io.mokamint.application.messages.BeginBlockResultMessages;
import io.mokamint.application.messages.CheckPrologExtraMessages;
import io.mokamint.application.messages.CheckPrologExtraResultMessages;
import io.mokamint.application.messages.CheckTransactionMessages;
import io.mokamint.application.messages.CheckTransactionResultMessages;
import io.mokamint.application.messages.CommitBlockMessages;
import io.mokamint.application.messages.CommitBlockResultMessages;
import io.mokamint.application.messages.DeliverTransactionMessages;
import io.mokamint.application.messages.DeliverTransactionResultMessages;
import io.mokamint.application.messages.EndBlockMessages;
import io.mokamint.application.messages.EndBlockResultMessages;
import io.mokamint.application.messages.GetBalanceMessages;
import io.mokamint.application.messages.GetBalanceResultMessages;
import io.mokamint.application.messages.GetInfoMessages;
import io.mokamint.application.messages.GetInfoResultMessages;
import io.mokamint.application.messages.GetInitialStateIdMessages;
import io.mokamint.application.messages.GetInitialStateIdResultMessages;
import io.mokamint.application.messages.GetPriorityMessages;
import io.mokamint.application.messages.GetPriorityResultMessages;
import io.mokamint.application.messages.GetRepresentationMessages;
import io.mokamint.application.messages.GetRepresentationResultMessages;
import io.mokamint.application.messages.KeepFromMessages;
import io.mokamint.application.messages.KeepFromResultMessages;
import io.mokamint.application.messages.PublishMessages;
import io.mokamint.application.messages.PublishResultMessages;
import io.mokamint.node.BlockDescriptions;
import io.mokamint.node.Blocks;
import io.mokamint.node.Transactions;
import io.mokamint.nonce.Challenges;
import io.mokamint.nonce.Deadlines;
import io.mokamint.nonce.Prologs;

public class MessagesTests extends AbstractLoggedTests {

	@Test
	@DisplayName("checkPrologExtra messages are correctly encoded into Json and decoded from Json")
	public void encodeDecodeWorksForCheckPrologExtra() throws Exception {
		var checkPrologExtraMessage1 = CheckPrologExtraMessages.of(new byte[] { 13, 1, 19, 73 }, "id");
		String encoded = new CheckPrologExtraMessages.Encoder().encode(checkPrologExtraMessage1);
		var checkPrologExtraMessage2 = new CheckPrologExtraMessages.Decoder().decode(encoded);
		assertEquals(checkPrologExtraMessage1, checkPrologExtraMessage2);
	}

	@Test
	@DisplayName("checkPrologExtraResult messages are correctly encoded into Json and decoded from Json")
	public void encodeDecodeWorksForCheckPrologExtraResult() throws Exception {
		var checkPrologExtraResultMessage1 = CheckPrologExtraResultMessages.of(true, "id");
		String encoded = new CheckPrologExtraResultMessages.Encoder().encode(checkPrologExtraResultMessage1);
		var checkPrologExtraResultMessage2 = new CheckPrologExtraResultMessages.Decoder().decode(encoded);
		assertEquals(checkPrologExtraResultMessage1, checkPrologExtraResultMessage2);
	}

	@Test
	@DisplayName("checkTransaction messages are correctly encoded into Json and decoded from Json")
	public void encodeDecodeWorksForCheckTransaction() throws Exception {
		var checkTransactionMessage1 = CheckTransactionMessages.of(Transactions.of(new byte[] { 13, 1, 19, 73 }), "id");
		String encoded = new CheckTransactionMessages.Encoder().encode(checkTransactionMessage1);
		var checkTransactionMessage2 = new CheckTransactionMessages.Decoder().decode(encoded);
		assertEquals(checkTransactionMessage1, checkTransactionMessage2);
	}

	@Test
	@DisplayName("checkTransaction result messages are correctly encoded into Json and decoded from Json")
	public void encodeDecodeWorksForCheckTransactionResult() throws Exception {
		var checkTransactionResultMessage1 = CheckTransactionResultMessages.of("id");
		String encoded = new CheckTransactionResultMessages.Encoder().encode(checkTransactionResultMessage1);
		var checkTransactionResultMessage2 = new CheckTransactionResultMessages.Decoder().decode(encoded);
		assertEquals(checkTransactionResultMessage1, checkTransactionResultMessage2);
	}

	@Test
	@DisplayName("deliverTransaction messages are correctly encoded into Json and decoded from Json")
	public void encodeDecodeWorksForDeliverTransaction() throws Exception {
		var deliverTransactionMessage1 = DeliverTransactionMessages.of(42, Transactions.of(new byte[] { 13, 1, 19, 73 }), "id");
		String encoded = new DeliverTransactionMessages.Encoder().encode(deliverTransactionMessage1);
		var deliverTransactionMessage2 = new DeliverTransactionMessages.Decoder().decode(encoded);
		assertEquals(deliverTransactionMessage1, deliverTransactionMessage2);
	}

	@Test
	@DisplayName("deliverTransaction result messages are correctly encoded into Json and decoded from Json")
	public void encodeDecodeWorksForDeliverTransactionResult() throws Exception {
		var deliverTransactionResultMessage1 = DeliverTransactionResultMessages.of("id");
		String encoded = new DeliverTransactionResultMessages.Encoder().encode(deliverTransactionResultMessage1);
		var deliverTransactionResultMessage2 = new DeliverTransactionResultMessages.Decoder().decode(encoded);
		assertEquals(deliverTransactionResultMessage1, deliverTransactionResultMessage2);
	}

	@Test
	@DisplayName("getInfo messages are correctly encoded into Json and decoded from Json")
	public void encodeDecodeWorksForGetInfo() throws Exception {
		var getInfoMessage1 = GetInfoMessages.of("id");
		String encoded = new GetInfoMessages.Encoder().encode(getInfoMessage1);
		var getInfoMessage2 = new GetInfoMessages.Decoder().decode(encoded);
		assertEquals(getInfoMessage1, getInfoMessage2);
	}

	@Test
	@DisplayName("getInfo result messages are correctly encoded into Json and decoded from Json")
	public void encodeDecodeWorksForGetInfoResult() throws Exception {
		var getInfoResultMessage1 = GetInfoResultMessages.of(Infos.of("Star Wars", "an fantastic application"), "id");
		String encoded = new GetInfoResultMessages.Encoder().encode(getInfoResultMessage1);
		var getInfoResultMessage2 = new GetInfoResultMessages.Decoder().decode(encoded);
		assertEquals(getInfoResultMessage1, getInfoResultMessage2);
	}

	@Test
	@DisplayName("getPriority messages are correctly encoded into Json and decoded from Json")
	public void encodeDecodeWorksForGetPriority() throws Exception {
		var getPriorityMessage1 = GetPriorityMessages.of(Transactions.of(new byte[] { 13, 1, 19, 73 }), "id");
		String encoded = new GetPriorityMessages.Encoder().encode(getPriorityMessage1);
		var getPriorityMessage2 = new GetPriorityMessages.Decoder().decode(encoded);
		assertEquals(getPriorityMessage1, getPriorityMessage2);
	}

	@Test
	@DisplayName("getPriority result messages are correctly encoded into Json and decoded from Json")
	public void encodeDecodeWorksForGetPriorityResult() throws Exception {
		var getPriorityResultMessage1 = GetPriorityResultMessages.of(1973L, "id");
		String encoded = new GetPriorityResultMessages.Encoder().encode(getPriorityResultMessage1);
		var getPriorityResultMessage2 = new GetPriorityResultMessages.Decoder().decode(encoded);
		assertEquals(getPriorityResultMessage1, getPriorityResultMessage2);
	}

	@Test
	@DisplayName("getRepresentation messages are correctly encoded into Json and decoded from Json")
	public void encodeDecodeWorksForGetRepresentation() throws Exception {
		var getRepresentationMessage1 = GetRepresentationMessages.of(Transactions.of(new byte[] { 13, 1, 19, 73 }), "id");
		String encoded = new GetRepresentationMessages.Encoder().encode(getRepresentationMessage1);
		var getRepresentationMessage2 = new GetRepresentationMessages.Decoder().decode(encoded);
		assertEquals(getRepresentationMessage1, getRepresentationMessage2);
	}

	@Test
	@DisplayName("getRepresentation result messages are correctly encoded into Json and decoded from Json")
	public void encodeDecodeWorksForGetRepresentationResult() throws Exception {
		var getRepresentationResultMessage1 = GetRepresentationResultMessages.of("h\"ello", "id");
		String encoded = new GetRepresentationResultMessages.Encoder().encode(getRepresentationResultMessage1);
		var getRepresentationResultMessage2 = new GetRepresentationResultMessages.Decoder().decode(encoded);
		assertEquals(getRepresentationResultMessage1, getRepresentationResultMessage2);
	}

	@Test
	@DisplayName("getInitialStateId messages are correctly encoded into Json and decoded from Json")
	public void encodeDecodeWorksForGetInitialStateId() throws Exception {
		var getInitialStateIdMessage1 = GetInitialStateIdMessages.of("id");
		String encoded = new GetInitialStateIdMessages.Encoder().encode(getInitialStateIdMessage1);
		var getInitialStateIdMessage2 = new GetInitialStateIdMessages.Decoder().decode(encoded);
		assertEquals(getInitialStateIdMessage1, getInitialStateIdMessage2);
	}

	@Test
	@DisplayName("getInitialStateId result messages are correctly encoded into Json and decoded from Json")
	public void encodeDecodeWorksForGetInitialStateIdResult() throws Exception {
		var getInitialStateIdResultMessage1 = GetInitialStateIdResultMessages.of(new byte[] { 1, 10, 100, 90, 87 }, "id");
		String encoded = new GetInitialStateIdResultMessages.Encoder().encode(getInitialStateIdResultMessage1);
		var getInitialStateIdResultMessage2 = new GetInitialStateIdResultMessages.Decoder().decode(encoded);
		assertEquals(getInitialStateIdResultMessage1, getInitialStateIdResultMessage2);
	}

	@Test
	@DisplayName("beginBlock messages are correctly encoded into Json and decoded from Json")
	public void encodeDecodeWorksForBeginBlock() throws Exception {
		var beginBlockMessage1 = BeginBlockMessages.of(1973, LocalDateTime.now(ZoneId.of("UTC")), new byte[] { 13, 1, 19, 73 }, "id");
		String encoded = new BeginBlockMessages.Encoder().encode(beginBlockMessage1);
		var beginBlockMessage2 = new BeginBlockMessages.Decoder().decode(encoded);
		assertEquals(beginBlockMessage1, beginBlockMessage2);
	}

	@Test
	@DisplayName("beginBlock result messages are correctly encoded into Json and decoded from Json")
	public void encodeDecodeWorksForBeginBlockResult() throws Exception {
		var beginBlockResultMessage1 = BeginBlockResultMessages.of(1973, "id");
		String encoded = new BeginBlockResultMessages.Encoder().encode(beginBlockResultMessage1);
		var beginBlockResultMessage2 = new BeginBlockResultMessages.Decoder().decode(encoded);
		assertEquals(beginBlockResultMessage1, beginBlockResultMessage2);
	}

	@Test
	@DisplayName("endBlock messages are correctly encoded into Json and decoded from Json")
	public void encodeDecodeWorksForEndBlock() throws Exception {
		var hashingForDeadlines = HashingAlgorithms.shabal256();
		var value = new byte[hashingForDeadlines.length()];
		for (int pos = 0; pos < value.length; pos++)
			value[pos] = (byte) pos;
		var hashingForGenerations = HashingAlgorithms.sha256();
		var generationSignature = new byte[hashingForGenerations.length()];
		for (int pos = 0; pos < generationSignature.length; pos++)
			generationSignature[pos] = (byte) (42 + pos);
		var ed25519 = SignatureAlgorithms.ed25519();
		var plotKeyPair = ed25519.getKeyPair();
		var prolog = Prologs.of("octopus", ed25519, ed25519.getKeyPair().getPublic(), ed25519, plotKeyPair.getPublic(), new byte[0]);
		var deadline = Deadlines.of(prolog, 13, value, Challenges.of(11, generationSignature, hashingForDeadlines, hashingForGenerations), plotKeyPair.getPrivate());
		var endBlockMessage1 = EndBlockMessages.of(13, deadline, "id");
		String encoded = new EndBlockMessages.Encoder().encode(endBlockMessage1);
		var endBlockMessage2 = new EndBlockMessages.Decoder().decode(encoded);
		assertEquals(endBlockMessage1, endBlockMessage2);
	}

	@Test
	@DisplayName("endBlock result messages are correctly encoded into Json and decoded from Json")
	public void encodeDecodeWorksForEndBlockResult() throws Exception {
		var endBlockResultMessage1 = EndBlockResultMessages.of(new byte[] { 1, 10, 100, 90, 87 }, "id");
		String encoded = new EndBlockResultMessages.Encoder().encode(endBlockResultMessage1);
		var endBlockResultMessage2 = new EndBlockResultMessages.Decoder().decode(encoded);
		assertEquals(endBlockResultMessage1, endBlockResultMessage2);
	}

	@Test
	@DisplayName("commitBlock messages are correctly encoded into Json and decoded from Json")
	public void encodeDecodeWorksForCommitBlock() throws Exception {
		var commitBlockMessage1 = CommitBlockMessages.of(13, "id");
		String encoded = new CommitBlockMessages.Encoder().encode(commitBlockMessage1);
		var commitBlockMessage2 = new CommitBlockMessages.Decoder().decode(encoded);
		assertEquals(commitBlockMessage1, commitBlockMessage2);
	}

	@Test
	@DisplayName("commitBlock result messages are correctly encoded into Json and decoded from Json")
	public void encodeDecodeWorksForCommitBlockResult() throws Exception {
		var commitBlockResultMessage1 = CommitBlockResultMessages.of("id");
		String encoded = new CommitBlockResultMessages.Encoder().encode(commitBlockResultMessage1);
		var commitBlockResultMessage2 = new CommitBlockResultMessages.Decoder().decode(encoded);
		assertEquals(commitBlockResultMessage1, commitBlockResultMessage2);
	}

	@Test
	@DisplayName("abortBlock messages are correctly encoded into Json and decoded from Json")
	public void encodeDecodeWorksForAbortBlock() throws Exception {
		var abortBlockMessage1 = AbortBlockMessages.of(13, "id");
		String encoded = new AbortBlockMessages.Encoder().encode(abortBlockMessage1);
		var abortBlockMessage2 = new AbortBlockMessages.Decoder().decode(encoded);
		assertEquals(abortBlockMessage1, abortBlockMessage2);
	}

	@Test
	@DisplayName("abortBlock result messages are correctly encoded into Json and decoded from Json")
	public void encodeDecodeWorksForAbortBlockResult() throws Exception {
		var abortBlockResultMessage1 = AbortBlockResultMessages.of("id");
		String encoded = new AbortBlockResultMessages.Encoder().encode(abortBlockResultMessage1);
		var abortBlockResultMessage2 = new AbortBlockResultMessages.Decoder().decode(encoded);
		assertEquals(abortBlockResultMessage1, abortBlockResultMessage2);
	}

	@Test
	@DisplayName("keepFrom messages are correctly encoded into Json and decoded from Json")
	public void encodeDecodeWorksForKeepFrom() throws Exception {
		var keepFromMessage1 = KeepFromMessages.of(LocalDateTime.now(), "id");
		String encoded = new KeepFromMessages.Encoder().encode(keepFromMessage1);
		var keepFromMessage2 = new KeepFromMessages.Decoder().decode(encoded);
		assertEquals(keepFromMessage1, keepFromMessage2);
	}

	@Test
	@DisplayName("keepFrom result messages are correctly encoded into Json and decoded from Json")
	public void encodeDecodeWorksForKeepFromResult() throws Exception {
		var keepFromResultMessage1 = KeepFromResultMessages.of("id");
		String encoded = new KeepFromResultMessages.Encoder().encode(keepFromResultMessage1);
		var keepFromResultMessage2 = new KeepFromResultMessages.Decoder().decode(encoded);
		assertEquals(keepFromResultMessage1, keepFromResultMessage2);
	}

	@Test
	@DisplayName("publish messages are correctly encoded into Json and decoded from Json")
	public void encodeDecodeWorksForPublish() throws Exception {
		var random = new Random();
		var ed25519 = SignatureAlgorithms.ed25519();
		var sha256 = HashingAlgorithms.sha256();

		var keysDeadline = ed25519.getKeyPair();
		var keysBlock = ed25519.getKeyPair();
		var prolog = Prologs.of("octopus", ed25519, keysBlock.getPublic(), ed25519, keysDeadline.getPublic(), new byte[5]);
		var previousHash = new byte[sha256.length()];
		random.nextBytes(previousHash);
		var value = new byte[sha256.length()];
		random.nextBytes(value);
		var generationSignature = new byte[sha256.length()];
		random.nextBytes(generationSignature);
		var challenge = Challenges.of(47, generationSignature, sha256, sha256);
		var deadline = Deadlines.of(prolog, 42L, value, challenge, keysDeadline.getPrivate());
		var description = BlockDescriptions.of(42L, BigInteger.TEN, 1000L, 180L, BigInteger.TWO, deadline, previousHash, 4000, 256, sha256, sha256);
		var bytes1 = new byte[100];
		random.nextBytes(bytes1);
		var bytes2 = new byte[60];
		random.nextBytes(bytes2);
		var bytes3 = new byte[113];
		random.nextBytes(bytes3);
		var transaction1 = Transactions.of(bytes1);
		var transaction2 = Transactions.of(bytes2);
		var transaction3 = Transactions.of(bytes3);
		var stateId = new byte[87];
		random.nextBytes(stateId);
		var block = Blocks.of(description, Stream.of(transaction1, transaction2, transaction3), stateId, keysBlock.getPrivate());

		var publishMessage1 = PublishMessages.of(block, "id");
		String encoded = new PublishMessages.Encoder().encode(publishMessage1);
		var publishMessage2 = new PublishMessages.Decoder().decode(encoded);
		assertEquals(publishMessage1, publishMessage2);
	}

	@Test
	@DisplayName("publish result messages are correctly encoded into Json and decoded from Json")
	public void encodeDecodeWorksForPublishResult() throws Exception {
		var publishResultMessage1 = PublishResultMessages.of("id");
		String encoded = new PublishResultMessages.Encoder().encode(publishResultMessage1);
		var publishResultMessage2 = new PublishResultMessages.Decoder().decode(encoded);
		assertEquals(publishResultMessage1, publishResultMessage2);
	}

	@Test
	@DisplayName("getBalance messages are correctly encoded into Json and decoded from Json")
	public void encodeDecodeWorksForGetBalance() throws Exception {
		var ed25519 = SignatureAlgorithms.ed25519();
		var publicKey = ed25519.getKeyPair().getPublic();
		var getBalanceMessage1 = GetBalanceMessages.of(ed25519, publicKey, "id");
		String encoded = new GetBalanceMessages.Encoder().encode(getBalanceMessage1);
		var getBalanceMessage2 = new GetBalanceMessages.Decoder().decode(encoded);
		assertEquals(getBalanceMessage1, getBalanceMessage2);
	}

	@Test
	@DisplayName("getBalanceResult non-empty messages are correctly encoded into Json and decoded from Json")
	public void encodeDecodeWorksForGetBalanceNonEmptyResult() throws Exception {
		var balance = Optional.of(BigInteger.TEN);
		var getBalanceResultMessage1 = GetBalanceResultMessages.of(balance, "id");
		String encoded = new GetBalanceResultMessages.Encoder().encode(getBalanceResultMessage1);
		var getBalanceResultMessage2 = new GetBalanceResultMessages.Decoder().decode(encoded);
		assertEquals(getBalanceResultMessage1, getBalanceResultMessage2);
	}

	@Test
	@DisplayName("getBalanceResult empty messages are correctly encoded into Json and decoded from Json")
	public void encodeDecodeWorksForGetBalanceEmptyResult() throws Exception {
		var balance = Optional.<BigInteger> empty();
		var getBalanceResultMessage1 = GetBalanceResultMessages.of(balance, "id");
		String encoded = new GetBalanceResultMessages.Encoder().encode(getBalanceResultMessage1);
		var getBalanceResultMessage2 = new GetBalanceResultMessages.Decoder().decode(encoded);
		assertEquals(getBalanceResultMessage1, getBalanceResultMessage2);
	}
}