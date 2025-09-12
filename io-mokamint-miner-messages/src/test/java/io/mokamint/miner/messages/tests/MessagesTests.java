/*
Copyright 2025 Fausto Spoto

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

package io.mokamint.miner.messages.tests;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.math.BigInteger;
import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import io.hotmoka.crypto.HashingAlgorithms;
import io.hotmoka.crypto.SignatureAlgorithms;
import io.hotmoka.testing.AbstractLoggedTests;
import io.mokamint.miner.MiningSpecifications;
import io.mokamint.miner.messages.GetBalanceMessages;
import io.mokamint.miner.messages.GetBalanceResultMessages;
import io.mokamint.miner.messages.GetMiningSpecificationMessages;
import io.mokamint.miner.messages.GetMiningSpecificationResultMessages;

public class MessagesTests extends AbstractLoggedTests {

	@Test
	@DisplayName("getMiningSpecification messages are correctly encoded into Json and decoded from Json")
	public void encodeDecodeWorksForGetMiningSpecification() throws Exception {
		var getMiningSpecificationMessage1 = GetMiningSpecificationMessages.of("id");
		String encoded = new GetMiningSpecificationMessages.Encoder().encode(getMiningSpecificationMessage1);
		var getMiningSpecificationMessage2 = new GetMiningSpecificationMessages.Decoder().decode(encoded);
		assertEquals(getMiningSpecificationMessage1, getMiningSpecificationMessage2);
	}

	@Test
	@DisplayName("getMiningSpecificationResult messages are correctly encoded into Json and decoded from Json")
	public void encodeDecodeWorksForGetMiningSpecificationResult() throws Exception {
		var ed25519 = SignatureAlgorithms.ed25519();
		var miningSpecification = MiningSpecifications.of("name", "description", "octopus", HashingAlgorithms.shabal256(), ed25519, ed25519, ed25519.getKeyPair().getPublic());
		var getMiningSpecificationResultMessage1 = GetMiningSpecificationResultMessages.of(miningSpecification, "id");
		String encoded = new GetMiningSpecificationResultMessages.Encoder().encode(getMiningSpecificationResultMessage1);
		var getMiningSpecificationResultMessage2 = new GetMiningSpecificationResultMessages.Decoder().decode(encoded);
		assertEquals(getMiningSpecificationResultMessage1, getMiningSpecificationResultMessage2);
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