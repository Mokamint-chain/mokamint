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

package io.mokamint.miner.remote.tests;

import static io.hotmoka.crypto.HashingAlgorithms.sha256;
import static io.hotmoka.crypto.HashingAlgorithms.shabal256;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.URI;
import java.security.NoSuchAlgorithmException;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import io.hotmoka.crypto.HashingAlgorithms;
import io.hotmoka.crypto.SignatureAlgorithms;
import io.hotmoka.testing.AbstractLoggedTests;
import io.mokamint.miner.MiningSpecifications;
import io.mokamint.miner.api.MiningSpecification;
import io.mokamint.miner.remote.RemoteMiners;
import io.mokamint.nonce.Challenges;
import io.mokamint.nonce.Deadlines;
import io.mokamint.nonce.Prologs;
import io.mokamint.nonce.api.Challenge;
import io.mokamint.nonce.api.Deadline;

public class RemoteMinerTests extends AbstractLoggedTests {

	/**
	 * The mining specification of the test miners.
	 */
	private final static MiningSpecification MINING_SPECIFICATION;

	static {
		try {
			var ed25519 = SignatureAlgorithms.ed25519();
			MINING_SPECIFICATION = MiningSpecifications.of("octopus", HashingAlgorithms.shabal256(), ed25519, ed25519, ed25519.getKeyPair().getPublic());
		}
		catch (NoSuchAlgorithmException e) {
			throw new RuntimeException(e);
		}
	}

	@Test
	@DisplayName("if a deadline description is requested to a remote miner, it gets forwarded to the connected service(s)")
	public void remoteMinerForwardsToServices() throws Exception {
		var semaphore = new Semaphore(0);
		var shabal256 = shabal256();
		var hashingForGenerations = sha256();
		var generationSignature = new byte[hashingForGenerations.length()];
		for (int pos = 0; pos < generationSignature.length; pos++)
			generationSignature[pos] = (byte) (42 + pos);
		var description = Challenges.of(42, generationSignature, shabal256, hashingForGenerations);

		Consumer<Challenge> onDeadlineDescriptionReceived = received -> {
			if (description.equals(received))
				semaphore.release();
		};

		try (var remote = RemoteMiners.open(8025, MINING_SPECIFICATION, _deadline -> {});
			 var client1 = new TestClient(URI.create("ws://localhost:8025"), onDeadlineDescriptionReceived);
			 var client2 = new TestClient(URI.create("ws://localhost:8025"), onDeadlineDescriptionReceived)) {
			remote.requestDeadline(description, deadline -> {});
			assertTrue(semaphore.tryAcquire(2, 1, TimeUnit.SECONDS));
		}
	}

	@Test
	@DisplayName("if a client sends a deadline, it reaches the requester of the corresponding challenge")
	public void remoteMinerForwardsToCorrespondingRequester() throws Exception {
		var semaphore = new Semaphore(0);
		var shabal256 = shabal256();
		var hashingForGenerations = sha256();
		var generationSignature = new byte[hashingForGenerations.length()];
		for (int pos = 0; pos < generationSignature.length; pos++)
			generationSignature[pos] = (byte) (42 + pos);
		var value = new byte[shabal256.length()];
		for (int pos = 0; pos < value.length; pos++)
			value[pos] = (byte) pos;
		int scoopNumber = 42;
		var challenge = Challenges.of(scoopNumber, generationSignature, shabal256, hashingForGenerations);
		var ed25519 = SignatureAlgorithms.ed25519();
		var nodePublicKey = ed25519.getKeyPair().getPublic();
		var plotKeyPair = ed25519.getKeyPair();
		var prolog = Prologs.of("octopus", ed25519, nodePublicKey, ed25519, plotKeyPair.getPublic(), new byte[0]);
		var deadline = Deadlines.of(prolog, 43L, value, challenge, plotKeyPair.getPrivate());

		Consumer<Deadline> onDeadlineReceived = received -> {
			if (deadline.equals(received))
				semaphore.release();
		};

		try (var remote = RemoteMiners.open(8025, MINING_SPECIFICATION, _deadline -> {}); var client = new TestClient(new URI("ws://localhost:8025"), _challenge -> {})) {
			remote.requestDeadline(challenge, onDeadlineReceived);
			remote.requestDeadline(challenge, onDeadlineReceived);
			client.send(deadline);
			assertTrue(semaphore.tryAcquire(2, 1, TimeUnit.SECONDS));
		}
	}

	@Test
	@DisplayName("if a client sends a deadline, it does not reach the requester of another challenge")
	public void remoteMinerDoesNotForwardToWrongRequester() throws Exception {
		var semaphore = new Semaphore(0);
		var shabal256 = shabal256();
		var hashingForGenerations = sha256();
		var generationSignature = new byte[hashingForGenerations.length()];
		for (int pos = 0; pos < generationSignature.length; pos++)
			generationSignature[pos] = (byte) (42 + pos);
		int scoopNumber = 42;
		var challenge = Challenges.of(scoopNumber, generationSignature, shabal256, hashingForGenerations);
		var value = new byte[shabal256.length()];
		for (int pos = 0; pos < value.length; pos++)
			value[pos] = (byte) pos;
		var ed25519 = SignatureAlgorithms.ed25519();
		var nodePublicKey = ed25519.getKeyPair().getPublic();
		var plotKeyPair = ed25519.getKeyPair();
		var prolog = Prologs.of("octopus", ed25519, nodePublicKey, ed25519, plotKeyPair.getPublic(), new byte[0]);
		var deadline = Deadlines.of(prolog, 43L, value, Challenges.of(scoopNumber + 1, generationSignature, shabal256, hashingForGenerations), plotKeyPair.getPrivate()); // <-- +1

		Consumer<Deadline> onDeadlineReceived = received -> {
			if (deadline.equals(received))
				semaphore.release();
		};

		try (var remote = RemoteMiners.open(8025, MINING_SPECIFICATION, _deadline -> {}); var client = new TestClient(new URI("ws://localhost:8025"), _description -> {})) {
			remote.requestDeadline(challenge, onDeadlineReceived);
			client.send(deadline);
			assertFalse(semaphore.tryAcquire(1, 1, TimeUnit.SECONDS));
		}
	}

	@Test
	@DisplayName("if a client has been closed, it does not receive descriptions anymore")
	public void remoteMinerDoesNotForwardDescriptionToClosedClient() throws Exception {
		var semaphore = new Semaphore(0);
		var hashingForGenerations = sha256();
		var generationSignature = new byte[hashingForGenerations.length()];
		for (int pos = 0; pos < generationSignature.length; pos++)
			generationSignature[pos] = (byte) (42 + pos);
		var description = Challenges.of(42, generationSignature, shabal256(), hashingForGenerations);

		try (var remote = RemoteMiners.open(8025, MINING_SPECIFICATION, _deadline -> {});
			 var client = new TestClient(new URI("ws://localhost:8025"), _description -> semaphore.release())) {

			client.close();
			remote.requestDeadline(description, _deadline -> {});
			assertFalse(semaphore.tryAcquire(1, 1, TimeUnit.SECONDS));
		}
	}
}