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
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import io.hotmoka.crypto.HashingAlgorithms;
import io.hotmoka.crypto.SignatureAlgorithms;
import io.hotmoka.testing.AbstractLoggedTests;
import io.hotmoka.websockets.beans.api.InconsistentJsonException;
import io.mokamint.node.BasicConsensusConfigBuilders;
import jakarta.websocket.DecodeException;

public class ConfigTests extends AbstractLoggedTests {

	@Test
	@DisplayName("configs are correctly encoded into Json and decoded from Json")
	public void encodeDecodeWorks() throws Exception {
		var config1 = BasicConsensusConfigBuilders.defaults()
			.setChainId("octopus")
			.setSignatureForBlocks(SignatureAlgorithms.ed25519())
			.setSignatureForDeadlines(SignatureAlgorithms.sha256dsa())
			.setHashingForTransactions(HashingAlgorithms.shabal256())
			.setMaxBlockSize(12345)
			.build();
		String encoded = new BasicConsensusConfigBuilders.Encoder().encode(config1);
		var config2 = new BasicConsensusConfigBuilders.Decoder().decode(encoded);
		assertEquals(config1, config2);
	}

	@Test
	@DisplayName("JSON of a config with missing property leads to a DecodeException")
	public void decodeFailsForWrongJSON() throws Exception {
		var config = BasicConsensusConfigBuilders.defaults()
			.setChainId("octopus")
			.setSignatureForBlocks(SignatureAlgorithms.ed25519())
			.setSignatureForDeadlines(SignatureAlgorithms.sha256dsa())
			.setHashingForTransactions(HashingAlgorithms.shabal256())
			.setMaxBlockSize(12345)
			.build();
		String encoded = new BasicConsensusConfigBuilders.Encoder().encode(config);
		// we remove the "chainId" property from the JSON
		String wronglyEncoded = "{" + encoded.substring(1 + "\"chainId\":\"octopus\",".length());

		DecodeException e = assertThrows(DecodeException.class, () -> new BasicConsensusConfigBuilders.Decoder().decode(wronglyEncoded));
		assertTrue(e.getCause() instanceof InconsistentJsonException);
		assertEquals("chainId cannot be null", e.getCause().getMessage());
	}

	@Test
	@DisplayName("configs are correctly dumped into TOML and reloaded from TOML")
	public void dumpLoadTOMLWorks(@TempDir Path dir) throws Exception {
		var path = dir.resolve("config.toml");
		var config1 = BasicConsensusConfigBuilders.defaults()
				.setChainId("octopus")
				.setSignatureForBlocks(SignatureAlgorithms.ed25519())
				.setSignatureForDeadlines(SignatureAlgorithms.sha256dsa())
				.setHashingForGenerations(HashingAlgorithms.identity32())
				.setMaxBlockSize(12345)
				.build();
		Files.writeString(path, config1.toToml(), StandardCharsets.UTF_8);
		var config2 = BasicConsensusConfigBuilders.load(path).build();
		assertEquals(config1, config2);
	}
}