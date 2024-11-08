/*
Copyright 2023 Fausto Spoto

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

package io.mokamint.node.tests;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.math.BigInteger;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.SignatureException;
import java.time.LocalDateTime;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import io.hotmoka.crypto.HashingAlgorithms;
import io.hotmoka.crypto.SignatureAlgorithms;
import io.hotmoka.testing.AbstractLoggedTests;
import io.mokamint.node.BlockDescriptions;
import io.mokamint.nonce.Challenges;
import io.mokamint.nonce.Deadlines;
import io.mokamint.nonce.Prologs;
import jakarta.websocket.DecodeException;
import jakarta.websocket.EncodeException;

public class BlockDescriptionTests extends AbstractLoggedTests {

	@Test
	@DisplayName("genesis block descriptions are correctly encoded into Json and decoded from Json")
	public void encodeDecodeWorksForGenesis() throws EncodeException, DecodeException, NoSuchAlgorithmException, InvalidKeyException, SignatureException {
		var ed25519 = SignatureAlgorithms.ed25519();
		var description1 = BlockDescriptions.genesis(LocalDateTime.now(), 4000, 20000, HashingAlgorithms.sha256(), HashingAlgorithms.sha256(), HashingAlgorithms.shabal256(), HashingAlgorithms.shabal256(), ed25519, ed25519.getKeyPair().getPublic());
		String encoded = new BlockDescriptions.Encoder().encode(description1);
		var description2 = new BlockDescriptions.Decoder().decode(encoded);
		assertEquals(description1, description2);
	}

	@Test
	@DisplayName("non-genesis block descriptions are correctly encoded into Json and decoded from Json")
	public void encodeDecodeWorksForNonGenesis() throws EncodeException, DecodeException, NoSuchAlgorithmException, InvalidKeyException, SignatureException {
		var hashingForGenerations = HashingAlgorithms.sha256();
		var hashingForBlocks = HashingAlgorithms.sha256();
		var generationSignature = new byte[hashingForGenerations.length()];
		for (int pos = 0; pos < generationSignature.length; pos++)
			generationSignature[pos] = (byte) (42 + pos);
		var hashingForDeadlines = HashingAlgorithms.shabal256();
		var value = new byte[hashingForDeadlines.length()];
		for (int pos = 0; pos < value.length; pos++)
			value[pos] = (byte) pos;
		var ed25519 = SignatureAlgorithms.ed25519();
		var nodeKeyPair = ed25519.getKeyPair();
		var plotKeyPair = ed25519.getKeyPair();
		var prolog = Prologs.of("octopus", ed25519, nodeKeyPair.getPublic(), ed25519, plotKeyPair.getPublic(), new byte[0]);
		var deadline = Deadlines.of(prolog, 13, value, Challenges.of(11, generationSignature, hashingForDeadlines, hashingForGenerations), plotKeyPair.getPrivate());
		var description1 = BlockDescriptions.of(13, BigInteger.TEN, 1234L, 1100L, BigInteger.valueOf(13011973), deadline, new byte[hashingForBlocks.length()], 4000, 20000, hashingForBlocks, HashingAlgorithms.sha256());
		String encoded = new BlockDescriptions.Encoder().encode(description1);
		var description2 = new BlockDescriptions.Decoder().decode(encoded);
		assertEquals(description1, description2);
	}
}