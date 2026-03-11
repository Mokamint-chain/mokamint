/*
Copyright 2026 Fausto Spoto

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

package io.mokamint.application.bitcoin.tests;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.math.BigInteger;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import io.hotmoka.crypto.SignatureAlgorithms;
import io.mokamint.application.bitcoin.SendRequest;

public class SendRequestsTests {

	@Test
	@DisplayName("Send requests are correctly marshalled into bytes and unmarshalled")
	public void marshalUnmarshalWorksSendRequest() throws Exception {
		var ed25519 = SignatureAlgorithms.ed25519();
		var keysOfSender = ed25519.getKeyPair();
		var amount = BigInteger.valueOf(459278L);
		var publicKeyOfReceiver = ed25519.getKeyPair().getPublic();
		var nonce = 12345L;
		var sendRequest = SendRequest.of(keysOfSender, amount, publicKeyOfReceiver, nonce);
		byte[] marshalled = sendRequest.toByteArray();
		var sendRequestUnmarshalled = SendRequest.from(marshalled);
		assertEquals(sendRequest, sendRequestUnmarshalled);
	}
}
