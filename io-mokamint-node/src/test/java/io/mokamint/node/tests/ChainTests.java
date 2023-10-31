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

import java.util.stream.Stream;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import io.hotmoka.testing.AbstractLoggedTests;
import io.mokamint.node.ChainPortions;
import jakarta.websocket.DecodeException;
import jakarta.websocket.EncodeException;

public class ChainTests extends AbstractLoggedTests {

	@Test
	@DisplayName("chains with hashes are correctly encoded into Json and decoded from Json")
	public void encodeDecodeWorksWithHashes() throws EncodeException, DecodeException {
		var hash1 = new byte[] { 1, 2, 4, 100, 12 };
		var hash2 = new byte[] { 13, 20, 4, 99, 12, 11 };
		var chain1 = ChainPortions.of(Stream.of(hash1, hash2));
		String encoded = new ChainPortions.Encoder().encode(chain1);
		var chain2 = new ChainPortions.Decoder().decode(encoded);
		assertEquals(chain1, chain2);
	}

	@Test
	@DisplayName("chains with no hashes are correctly encoded into Json and decoded from Json")
	public void encodeDecodeWorksWithoutHashes() throws EncodeException, DecodeException {
		var chain1 = ChainPortions.of(Stream.empty());
		String encoded = new ChainPortions.Encoder().encode(chain1);
		var chain2 = new ChainPortions.Decoder().decode(encoded);
		assertEquals(chain1, chain2);
	}
}