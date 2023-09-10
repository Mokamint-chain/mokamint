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
import java.util.UUID;
import java.util.logging.LogManager;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import io.mokamint.node.MinerInfos;
import jakarta.websocket.DecodeException;
import jakarta.websocket.EncodeException;

public class MinerInfoTests {

	@Test
	@DisplayName("miner information is correctly encoded into Json and decoded from Json")
	public void encodeDecodeWorks() throws EncodeException, DecodeException {
		var minerInfo1 = MinerInfos.of(UUID.randomUUID(), 1234L, "a miner");
		String encoded = new MinerInfos.Encoder().encode(minerInfo1);
		var minerInfo2 = new MinerInfos.Decoder().decode(encoded);
		assertEquals(minerInfo1, minerInfo2);
	}

	static {
		String current = System.getProperty("java.util.logging.config.file");
		if (current == null) {
			// if the property is not set, we provide a default (if it exists)
			URL resource = MinerInfoTests.class.getClassLoader().getResource("logging.properties");
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