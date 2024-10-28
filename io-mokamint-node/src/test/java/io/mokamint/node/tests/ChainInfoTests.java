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

import java.security.NoSuchAlgorithmException;
import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import io.hotmoka.testing.AbstractLoggedTests;
import io.mokamint.node.ChainInfos;
import jakarta.websocket.DecodeException;
import jakarta.websocket.EncodeException;

public class ChainInfoTests extends AbstractLoggedTests {

	@Test
	@DisplayName("chain infos with non-empty hashes are correctly encoded into Json and decoded from Json")
	public void encodeDecodeWorksForNonEmpty() throws EncodeException, DecodeException, NoSuchAlgorithmException {
		var info1 = ChainInfos.of(1317L, Optional.of(new byte[] { 3, 6, 8, 11 }), Optional.of(new byte[] { 0, 90, 91 }), Optional.of(new byte[] { 13, 17, 19 }));
		String encoded = new ChainInfos.Encoder().encode(info1);
		var info2 = new ChainInfos.Decoder().decode(encoded);
		assertEquals(info1, info2);
	}

	@Test
	@DisplayName("chain infos with non-empty hashes and empty state id are correctly encoded into Json and decoded from Json")
	public void encodeDecodeWorksForNonEmptyWithEmptyStateId() throws EncodeException, DecodeException, NoSuchAlgorithmException {
		var info1 = ChainInfos.of(1317L, Optional.of(new byte[] { 3, 6, 8, 11 }), Optional.of(new byte[] { 0, 90, 91 }), Optional.empty());
		String encoded = new ChainInfos.Encoder().encode(info1);
		var info2 = new ChainInfos.Decoder().decode(encoded);
		assertEquals(info1, info2);
	}

	@Test
	@DisplayName("chain infos with empty hashes are correctly encoded into Json and decoded from Json")
	public void encodeDecodeWorksForEmpty() throws EncodeException, DecodeException, NoSuchAlgorithmException {
		var info1 = ChainInfos.of(0, Optional.empty(), Optional.empty(), Optional.empty());
		String encoded = new ChainInfos.Encoder().encode(info1);
		var info2 = new ChainInfos.Decoder().decode(encoded);
		assertEquals(info1, info2);
	}
}