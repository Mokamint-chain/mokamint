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

import java.security.NoSuchAlgorithmException;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import io.hotmoka.crypto.HashingAlgorithms;
import io.hotmoka.testing.AbstractLoggedTests;
import io.mokamint.nonce.Challenges;
import jakarta.websocket.DecodeException;
import jakarta.websocket.EncodeException;

public class ChallengeTests extends AbstractLoggedTests {

	@Test
	@DisplayName("challenges are correctly encoded into Json and decoded from Json")
	public void encodeDecodeWorksForChallenges() throws EncodeException, DecodeException, NoSuchAlgorithmException {
		var challenge1 = Challenges.of(13, new byte[] { 4, 5, 6 }, HashingAlgorithms.shabal256(), HashingAlgorithms.sha256());
		String encoded = new Challenges.Encoder().encode(challenge1);
		var challenge2 = new Challenges.Decoder().decode(encoded);
		assertEquals(challenge1, challenge2);
	}
}