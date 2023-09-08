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

import java.io.IOException;
import java.net.URL;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.function.Function;
import java.util.logging.LogManager;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import io.hotmoka.crypto.SignatureAlgorithms;
import io.mokamint.nonce.Prologs;
import jakarta.websocket.DecodeException;
import jakarta.websocket.EncodeException;

public class PrologTests {

	@Test
	@DisplayName("prologs are correctly encoded into Json and decoded from Json")
	public void encodeDecodeWorksForPrologs() throws EncodeException, DecodeException, NoSuchAlgorithmException, InvalidKeyException {
		var signature = SignatureAlgorithms.ed25519(Function.identity());
		var prolog1 = Prologs.of("octopus", signature.getKeyPair().getPublic(), signature.getKeyPair().getPublic(), new byte[] { 1, 2, 4 });
		String encoded = new Prologs.Encoder().encode(prolog1);
		System.out.println(encoded);
		var prolog2 = new Prologs.Decoder().decode(encoded);
		assertEquals(prolog1, prolog2);
	}

	static {
		String current = System.getProperty("java.util.logging.config.file");
		if (current == null) {
			// if the property is not set, we provide a default (if it exists)
			URL resource = PrologTests.class.getClassLoader().getResource("logging.properties");
			if (resource != null)
				try (var is = resource.openStream()) {
					LogManager.getLogManager().readConfiguration(is);
				}
				catch (SecurityException | IOException e) {
					throw new RuntimeException("Cannot load logging.properties file", e);
				}
		}
	}
}