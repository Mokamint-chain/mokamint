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

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

import java.net.URI;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import io.hotmoka.crypto.HashingAlgorithms;
import io.hotmoka.crypto.SignatureAlgorithms;
import io.hotmoka.testing.AbstractLoggedTests;
import io.mokamint.miner.MiningSpecifications;
import io.mokamint.miner.api.Miner;
import io.mokamint.miner.api.MinerException;
import io.mokamint.miner.messages.api.GetMiningSpecificationResultMessage;
import io.mokamint.miner.remote.RemoteMiners;
import io.mokamint.miner.service.internal.MinerServiceImpl;

public class RemoteMinerTests extends AbstractLoggedTests {
	private final static int PORT = 8025;
	private final static URI URI = java.net.URI.create("ws://localhost:" + PORT);
	private final static String ID = "id";

	private Miner mkMiner() {
		return mock();
	}

	@Test
	@DisplayName("if a getMiningSpecification() request reaches the remote, it sends back the result of the check")
	public void remoteGetMiningSpecificationWorks() throws Exception {
		var semaphore = new Semaphore(0);
		var miner = mkMiner();
		var ed25519 = SignatureAlgorithms.ed25519();
		var miningSpecification = MiningSpecifications.of("octopus", HashingAlgorithms.shabal256(), ed25519, ed25519, ed25519.getKeyPair().getPublic());

		class MinerServiceTest extends MinerServiceImpl {

			public MinerServiceTest() throws MinerException {
				super(miner, URI);
			}

			@Override
			protected void onGetMiningSpecificationResult(GetMiningSpecificationResultMessage message) {
				if (ID.equals(message.getId()) && message.get().equals(miningSpecification))
					semaphore.release();
			}

			private void sendGetMiningSpecification() throws MinerException {
				sendGetMiningSpecification(ID);
			}
		}

		try (var service = RemoteMiners.open(PORT, miningSpecification, deadline -> {}); var client = new MinerServiceTest()) {
			client.sendGetMiningSpecification();
			assertTrue(semaphore.tryAcquire(1, 1, TimeUnit.SECONDS));
		}
	}
}