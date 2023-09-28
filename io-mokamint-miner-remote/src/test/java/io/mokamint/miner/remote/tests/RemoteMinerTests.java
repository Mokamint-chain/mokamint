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

import static io.hotmoka.crypto.HashingAlgorithms.shabal256;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.function.Function;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import io.hotmoka.crypto.SignatureAlgorithms;
import io.hotmoka.testing.AbstractLoggedTests;
import io.mokamint.miner.remote.RemoteMiners;
import io.mokamint.nonce.DeadlineDescriptions;
import io.mokamint.nonce.Deadlines;
import io.mokamint.nonce.Prologs;
import io.mokamint.nonce.api.Deadline;
import io.mokamint.nonce.api.DeadlineDescription;
import jakarta.websocket.DeploymentException;

public class RemoteMinerTests extends AbstractLoggedTests {

	@Test
	@DisplayName("if a deadline description is requested to a remote miner, it gets forwarded to the connected service(s)")
	public void remoteMinerForwardsToServices() throws DeploymentException, IOException, URISyntaxException, InterruptedException, NoSuchAlgorithmException {
		var semaphore = new Semaphore(0);
		var shabal256 = shabal256(Function.identity());
		var description = DeadlineDescriptions.of(42, new byte[] { 1, 2, 3, 4, 5, 6 }, shabal256);

		Consumer<DeadlineDescription> onDeadlineDescriptionReceived = received -> {
			if (description.equals(received))
				semaphore.release();
		};

		try (var remote = RemoteMiners.of(8025, _deadline -> {});
			 var client1 = new TestClient(new URI("ws://localhost:8025"), onDeadlineDescriptionReceived);
			 var client2 = new TestClient(new URI("ws://localhost:8025"), onDeadlineDescriptionReceived)) {
			remote.requestDeadline(description, deadline -> {});
			assertTrue(semaphore.tryAcquire(2, 1, TimeUnit.SECONDS));
		}
	}

	@Test
	@DisplayName("if a client sends a deadline, it reaches the requester of the corresponding description")
	public void remoteMinerForwardsToCorrespondingRequester() throws DeploymentException, IOException, URISyntaxException, InterruptedException, NoSuchAlgorithmException, InvalidKeyException {
		var semaphore = new Semaphore(0);
		var shabal256 = shabal256(Function.identity());
		var value = new byte[shabal256.length()];
		for (int pos = 0; pos < value.length; pos++)
			value[pos] = (byte) pos;
		var data = new byte[] { 1, 2, 3, 4, 5, 6 };
		int scoopNumber = 42;
		var description = DeadlineDescriptions.of(scoopNumber, data, shabal256);
		var ed25519 = SignatureAlgorithms.ed25519();
		var nodePublicKey = ed25519.getKeyPair().getPublic();
		var prolog = Prologs.of("octopus", ed25519, nodePublicKey, ed25519, ed25519.getKeyPair().getPublic(), new byte[0]);
		var deadline = Deadlines.of(prolog, 43L, value, scoopNumber, data, shabal256);

		Consumer<Deadline> onDeadlineReceived = received -> {
			if (deadline.equals(received))
				semaphore.release();
		};

		try (var remote = RemoteMiners.of(8025, _deadline -> {}); var client = new TestClient(new URI("ws://localhost:8025"), _description -> {})) {
			remote.requestDeadline(description, onDeadlineReceived);
			remote.requestDeadline(description, onDeadlineReceived);
			client.send(deadline);
			assertTrue(semaphore.tryAcquire(2, 1, TimeUnit.SECONDS));
		}
	}

	@Test
	@DisplayName("if a client sends a deadline, it does not reach the requester of another description")
	public void remoteMinerDoesNotForwardToWrongRequester() throws DeploymentException, IOException, URISyntaxException, InterruptedException, NoSuchAlgorithmException, InvalidKeyException {
		var semaphore = new Semaphore(0);
		var shabal256 = shabal256(Function.identity());
		var data = new byte[] { 1, 2, 3, 4, 5, 6 };
		int scoopNumber = 42;
		var description = DeadlineDescriptions.of(scoopNumber, data, shabal256);
		var value = new byte[shabal256.length()];
		for (int pos = 0; pos < value.length; pos++)
			value[pos] = (byte) pos;
		var ed25519 = SignatureAlgorithms.ed25519();
		var nodePublicKey = ed25519.getKeyPair().getPublic();
		var prolog = Prologs.of("octopus", ed25519, nodePublicKey, ed25519, ed25519.getKeyPair().getPublic(), new byte[0]);
		var deadline = Deadlines.of(prolog, 43L, value, scoopNumber + 1, data, shabal256); // <-- +1

		Consumer<Deadline> onDeadlineReceived = received -> {
			if (deadline.equals(received))
				semaphore.release();
		};

		try (var remote = RemoteMiners.of(8025, _deadline -> {}); var client = new TestClient(new URI("ws://localhost:8025"), _description -> {})) {
			remote.requestDeadline(description, onDeadlineReceived);
			client.send(deadline);
			assertFalse(semaphore.tryAcquire(1, 1, TimeUnit.SECONDS));
		}
	}

	@Test
	@DisplayName("if a client has been closed, it does not receive descriptions anymore")
	public void remoteMinerDoesNotForwardDescriptionToClosedClient() throws DeploymentException, IOException, URISyntaxException, InterruptedException, NoSuchAlgorithmException {
		var semaphore = new Semaphore(0);
		var shabal256 = shabal256(Function.identity());
		var description = DeadlineDescriptions.of(42, new byte[] { 1, 2, 3, 4, 5, 6 }, shabal256);

		try (var remote = RemoteMiners.of(8025, _deadline -> {});
			 var client = new TestClient(new URI("ws://localhost:8025"), _description -> semaphore.release())) {

			client.close();
			remote.requestDeadline(description, _deadline -> {});
			assertFalse(semaphore.tryAcquire(1, 1, TimeUnit.SECONDS));
		}
	}
}