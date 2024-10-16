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

package io.mokamint.nonce.tests;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.SignatureException;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import io.hotmoka.crypto.HashingAlgorithms;
import io.hotmoka.crypto.SignatureAlgorithms;
import io.hotmoka.testing.AbstractLoggedTests;
import io.mokamint.nonce.Challenges;
import io.mokamint.nonce.Deadlines;
import io.mokamint.nonce.Prologs;
import jakarta.websocket.DecodeException;
import jakarta.websocket.EncodeException;

public class DeadlineTests extends AbstractLoggedTests {

	@Test
	@DisplayName("deadlines are correctly encoded into Json and decoded from Json")
	public void encodeDecodeWorksForDeadlines() throws EncodeException, DecodeException, NoSuchAlgorithmException, InvalidKeyException, SignatureException {
		var hashingForDeadlines = HashingAlgorithms.shabal256();
		var value = new byte[hashingForDeadlines.length()];
		for (int pos = 0; pos < value.length; pos++)
			value[pos] = (byte) pos;
		var ed25519 = SignatureAlgorithms.ed25519();
		var plotKeyPair = ed25519.getKeyPair();
		var prolog = Prologs.of("octopus", ed25519, ed25519.getKeyPair().getPublic(), ed25519, plotKeyPair.getPublic(), new byte[0]);
		var deadline1 = Deadlines.of(prolog, 13, value, Challenges.of(11, new byte[] { 90, 91, 92 }, hashingForDeadlines, HashingAlgorithms.sha256()), plotKeyPair.getPrivate());
		String encoded = new Deadlines.Encoder().encode(deadline1);
		var deadline2 = new Deadlines.Decoder().decode(encoded);
		assertEquals(deadline1, deadline2);
	}
}