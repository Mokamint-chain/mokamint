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

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import io.hotmoka.crypto.SignatureAlgorithms;
import io.hotmoka.testing.AbstractLoggedTests;
import io.mokamint.nonce.Prologs;

public class PrologTests extends AbstractLoggedTests {

	@Test
	@DisplayName("prologs are correctly encoded into Json and decoded from Json")
	public void encodeDecodeWorksForPrologs() throws Exception {
		var ed25519 = SignatureAlgorithms.ed25519();
		var prolog1 = Prologs.of("octopus", ed25519, ed25519.getKeyPair().getPublic(), ed25519, ed25519.getKeyPair().getPublic(), new byte[] { 1, 2, 4 });
		String encoded = new Prologs.Encoder().encode(prolog1);
		var prolog2 = new Prologs.Decoder().decode(encoded);
		assertEquals(prolog1, prolog2);
	}
}