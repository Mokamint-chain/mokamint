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
import java.net.URL;
import java.security.NoSuchAlgorithmException;
import java.util.Optional;
import java.util.logging.LogManager;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import io.mokamint.node.ChainInfos;
import jakarta.websocket.DecodeException;
import jakarta.websocket.EncodeException;

public class ChainInfoTests {

	@Test
	@DisplayName("chain infos with non-empty hashes are correctly encoded into Json and decoded from Json")
	public void encodeDecodeWorksForNonEmpty() throws EncodeException, DecodeException, NoSuchAlgorithmException {
		var info1 = ChainInfos.of(1317L, Optional.of(new byte[] { 3, 6, 8, 11 }), Optional.of(new byte[] { 0, 90, 91 }));
		String encoded = new ChainInfos.Encoder().encode(info1);
		var info2 = new ChainInfos.Decoder().decode(encoded);
		assertEquals(info1, info2);
	}

	@Test
	@DisplayName("chain infos with empty hashes are correctly encoded into Json and decoded from Json")
	public void encodeDecodeWorksForEmpty1() throws EncodeException, DecodeException, NoSuchAlgorithmException {
		var info1 = ChainInfos.of(1317L, Optional.empty(), Optional.of(new byte[] { 0, 90, 91 }));
		String encoded = new ChainInfos.Encoder().encode(info1);
		var info2 = new ChainInfos.Decoder().decode(encoded);
		assertEquals(info1, info2);
	}

	@Test
	@DisplayName("chain infos with empty hashes are correctly encoded into Json and decoded from Json")
	public void encodeDecodeWorksForEmpty2() throws EncodeException, DecodeException, NoSuchAlgorithmException {
		var info1 = ChainInfos.of(1317L, Optional.of(new byte[] { 0, 90, 91 }), Optional.empty());
		String encoded = new ChainInfos.Encoder().encode(info1);
		var info2 = new ChainInfos.Decoder().decode(encoded);
		assertEquals(info1, info2);
	}

	@Test
	@DisplayName("chain infos with empty hashes are correctly encoded into Json and decoded from Json")
	public void encodeDecodeWorksForEmpty3() throws EncodeException, DecodeException, NoSuchAlgorithmException {
		var info1 = ChainInfos.of(1317L, Optional.empty(), Optional.empty());
		String encoded = new ChainInfos.Encoder().encode(info1);
		var info2 = new ChainInfos.Decoder().decode(encoded);
		assertEquals(info1, info2);
	}

	static {
		String current = System.getProperty("java.util.logging.config.file");
		if (current == null) {
			// if the property is not set, we provide a default (if it exists)
			URL resource = ChainInfoTests.class.getClassLoader().getResource("logging.properties");
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