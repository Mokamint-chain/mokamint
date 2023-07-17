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
import java.net.URL;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.logging.LogManager;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import io.mokamint.miner.remote.RemoteMiners;
import io.mokamint.nonce.DeadlineDescriptions;
import io.mokamint.nonce.Deadlines;
import io.mokamint.nonce.api.Deadline;
import io.mokamint.nonce.api.DeadlineDescription;
import jakarta.websocket.DeploymentException;

public class RemoteMinerTests {

	@Test
	@DisplayName("if a deadline description is requested to a remote miner, it gets forwarded to the connected service(s)")
	public void remoteMinerForwardsToServices() throws DeploymentException, IOException, URISyntaxException, InterruptedException {
		var semaphore = new Semaphore(0);
		var shabal256 = shabal256(Function.identity());
		var description = DeadlineDescriptions.of(42, new byte[] { 1, 2, 3, 4, 5, 6 }, shabal256);

		Consumer<DeadlineDescription> onDeadlineDescriptionReceived = received -> {
			if (description.equals(received))
				semaphore.release();
		};

		try (var remote = RemoteMiners.of(8025);
			 var client1 = new TestClient(new URI("ws://localhost:8025"), onDeadlineDescriptionReceived);
			 var client2 = new TestClient(new URI("ws://localhost:8025"), onDeadlineDescriptionReceived)) {
			remote.requestDeadline(description, deadline -> {});
			assertTrue(semaphore.tryAcquire(2, 1, TimeUnit.SECONDS));
		}
	}

	@Test
	@DisplayName("if a client sends a deadline, it reaches the requester of the corresponding description")
	public void remoteMinerForwardsToCorrespondingRequester() throws DeploymentException, IOException, URISyntaxException, InterruptedException {
		var semaphore = new Semaphore(0);
		var shabal256 = shabal256(Function.identity());
		var data = new byte[] { 1, 2, 3, 4, 5, 6 };
		int scoopNumber = 42;
		var description = DeadlineDescriptions.of(scoopNumber, data, shabal256);
		var deadline = Deadlines.of(new byte[] { 13, 44, 17, 19 }, 43L, data, scoopNumber, data, shabal256);

		Consumer<Deadline> onDeadlineReceived = received -> {
			if (deadline.equals(received))
				semaphore.release();
		};

		try (var remote = RemoteMiners.of(8025); var client = new TestClient(new URI("ws://localhost:8025"), _description -> {})) {
			remote.requestDeadline(description, onDeadlineReceived);
			remote.requestDeadline(description, onDeadlineReceived);
			client.send(deadline);
			assertTrue(semaphore.tryAcquire(2, 1, TimeUnit.SECONDS));
		}
	}

	@Test
	@DisplayName("if a client sends a deadline, it does not reach the requester of another description")
	public void remoteMinerDoesNotForwardToWrongRequester() throws DeploymentException, IOException, URISyntaxException, InterruptedException {
		var semaphore = new Semaphore(0);
		var shabal256 = shabal256(Function.identity());
		var data = new byte[] { 1, 2, 3, 4, 5, 6 };
		int scoopNumber = 42;
		var description = DeadlineDescriptions.of(scoopNumber, data, shabal256);
		var deadline = Deadlines.of(new byte[] { 13, 44, 17, 19 }, 43L, data, scoopNumber + 1, data, shabal256); // <-- +1

		Consumer<Deadline> onDeadlineReceived = received -> {
			if (deadline.equals(received))
				semaphore.release();
		};

		try (var remote = RemoteMiners.of(8025); var client = new TestClient(new URI("ws://localhost:8025"), _description -> {})) {
			remote.requestDeadline(description, onDeadlineReceived);
			client.send(deadline);
			assertFalse(semaphore.tryAcquire(1, 1, TimeUnit.SECONDS));
		}
	}

	@Test
	@DisplayName("if a client has been closed, it does not receive descriptions anymore")
	public void remoteMinerDoesNotForwardDescriptionToClosedClient() throws DeploymentException, IOException, URISyntaxException, InterruptedException {
		var semaphore = new Semaphore(0);
		var shabal256 = shabal256(Function.identity());
		var description = DeadlineDescriptions.of(42, new byte[] { 1, 2, 3, 4, 5, 6 }, shabal256);
		Consumer<DeadlineDescription> onDeadlineDescriptionReceived = received -> semaphore.release();

		try (var remote = RemoteMiners.of(8025); var client = new TestClient(new URI("ws://localhost:8025"), onDeadlineDescriptionReceived)) {
			client.close();
			remote.requestDeadline(description, _deadline -> {});
			assertFalse(semaphore.tryAcquire(1, 1, TimeUnit.SECONDS));
		}
	}

	static {
		String current = System.getProperty("java.util.logging.config.file");
		if (current == null) {
			// if the property is not set, we provide a default (if it exists)
			URL resource = RemoteMinerTests.class.getClassLoader().getResource("logging.properties");
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