/*
Copyright 2025 Fausto Spoto

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

package io.mokamint.miner.integration.tests;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;

import java.net.URI;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import io.hotmoka.annotations.ThreadSafe;
import io.hotmoka.crypto.HashingAlgorithms;
import io.hotmoka.crypto.SignatureAlgorithms;
import io.hotmoka.testing.AbstractLoggedTests;
import io.mokamint.miner.MiningSpecifications;
import io.mokamint.miner.api.MinerException;
import io.mokamint.miner.api.MiningSpecification;
import io.mokamint.miner.messages.GetMiningSpecificationResultMessages;
import io.mokamint.miner.messages.api.GetMiningSpecificationMessage;
import io.mokamint.miner.remote.internal.RemoteMinerImpl;
import io.mokamint.miner.service.MinerServices;
import jakarta.websocket.Session;

public class MinerServiceTests extends AbstractLoggedTests {
	private final static int PORT = 8025;
	private final static URI URI = java.net.URI.create("ws://localhost:" + PORT);
	private final static MiningSpecification MINING_SPECIFICATION;

	static {
		try {
			var ed25519 = SignatureAlgorithms.ed25519();
			MINING_SPECIFICATION = MiningSpecifications.of("octopus", HashingAlgorithms.shabal256(), ed25519, ed25519, ed25519.getKeyPair().getPublic());
		}
		catch (NoSuchAlgorithmException | InvalidKeyException e) {
			throw new RuntimeException(e);
		}
	}

	/**
	 * Test server implementation.
	 */
	@ThreadSafe
	private static class RemoteMinerTest extends RemoteMinerImpl {

		/**
		 * Creates a new test remote miner.
		 * 
		 * @throws MinerException if the miner cannot be deployed
		 */
		private RemoteMinerTest() throws MinerException {
			super(PORT, MINING_SPECIFICATION, deadline -> {});
		}
	}

	@Test
	@DisplayName("getMiningSpecification() works")
	public void getMiningSpecificationWorks() throws Exception {
		class MyRemoteMiner extends RemoteMinerTest {

			private MyRemoteMiner() throws MinerException {}

			@Override
			protected void onGetMiningSpecification(GetMiningSpecificationMessage message, Session session) {
				sendObjectAsync(session, GetMiningSpecificationResultMessages.of(MINING_SPECIFICATION, message.getId()), RuntimeException::new);
			}
		};

		try (var remoteMiner = new MyRemoteMiner(); var minerService = MinerServices.of(mock(), URI, 30_000)) {
			assertEquals(MINING_SPECIFICATION, minerService.getMiningSpecification());
		}
	}
}