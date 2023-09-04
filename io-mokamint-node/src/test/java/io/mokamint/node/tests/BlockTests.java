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

import java.io.IOException;
import java.math.BigInteger;
import java.net.URL;
import java.time.LocalDateTime;
import java.util.function.Function;
import java.util.logging.LogManager;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import io.hotmoka.crypto.HashingAlgorithms;
import io.mokamint.node.Blocks;
import io.mokamint.nonce.Deadlines;
import jakarta.websocket.DecodeException;
import jakarta.websocket.EncodeException;

public class BlockTests {

	@Test
	@DisplayName("genesis blocks are correctly encoded into Json and decoded from Json")
	public void encodeDecodeWorksForGenesis() throws EncodeException, DecodeException {
		var block1 = Blocks.genesis(LocalDateTime.now(), BigInteger.ONE);
		String encoded = new Blocks.Encoder().encode(block1);
		var block2 = new Blocks.Decoder().decode(encoded);
		assertEquals(block1, block2);
	}

	@Test
	@DisplayName("non-genesis blocks are correctly encoded into Json and decoded from Json")
	public void encodeDecodeWorksNonGenesis() throws EncodeException, DecodeException {
		var hashing = HashingAlgorithms.shabal256(Function.identity());
		var value = new byte[hashing.length()];
		for (int pos = 0; pos < value.length; pos++)
			value[pos] = (byte) pos;
		var deadline = Deadlines.of(new byte[] {80, 81, 83}, 13, value, 11, new byte[] { 90, 91, 92 }, hashing);
		var block1 = Blocks.of(13, BigInteger.TEN, 1234L, 1100L, BigInteger.valueOf(13011973), deadline, new byte[] { 1, 2, 3, 4, 5, 6});
		String encoded = new Blocks.Encoder().encode(block1);
		var block2 = new Blocks.Decoder().decode(encoded);
		assertEquals(block1, block2);
	}

	static {
		String current = System.getProperty("java.util.logging.config.file");
		if (current == null) {
			// if the property is not set, we provide a default (if it exists)
			URL resource = BlockTests.class.getClassLoader().getResource("logging.properties");
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