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

package io.mokamint.miner.service.tests;

import static io.hotmoka.crypto.HashingAlgorithms.shabal256;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.logging.LogManager;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import io.mokamint.miner.api.Miner;
import io.mokamint.miner.service.MinerServices;
import io.mokamint.nonce.DeadlineDescriptions;
import io.mokamint.nonce.Deadlines;
import io.mokamint.nonce.api.Deadline;
import io.mokamint.nonce.api.DeadlineDescription;
import jakarta.websocket.DeploymentException;

public class MinerServiceTests {

	@Test
	@DisplayName("if a deadline description is requested to a miner service, it gets forwarded to the adapted miner")
	public void minerServiceForwardsToMiner() throws DeploymentException, IOException, URISyntaxException, InterruptedException, TimeoutException {
		var semaphore = new Semaphore(0);
		var description = DeadlineDescriptions.of(42, new byte[] { 1, 2, 3, 4, 5, 6 }, shabal256(Function.identity()));

		var miner = new Miner() {

			@Override
			public void requestDeadline(DeadlineDescription received, Consumer<Deadline> onDeadlineComputed) {
				if (description.equals(received))
					semaphore.release();
			}

			@Override
			public void close() {}
		};

		try (var requester = new TestServer(8025, deadline -> {}); var service = MinerServices.open(miner, new URI("ws://localhost:8025"))) {
			requester.requestDeadline(description);
			assertTrue(semaphore.tryAcquire(1, 1, TimeUnit.SECONDS));
		}
	}

	@Test
	@DisplayName("if the miner sends a deadline, it gets forwarded to the requester")
	public void minerForwardsToRequester() throws DeploymentException, IOException, URISyntaxException, InterruptedException, TimeoutException {
		var semaphore = new Semaphore(0);
		var shabal256 = shabal256(Function.identity());
		var description = DeadlineDescriptions.of(42, new byte[] { 1, 2, 3, 4, 5, 6 }, shabal256);
		var deadline = Deadlines.of(new byte[] { 13, 42, 17, 19 }, 42L, new byte[] { 1, 2, 3, 4, 5, 6 }, 11, new byte[] { 1, 2, 3 }, shabal256);

		var miner = new Miner() {

			@Override
			public void requestDeadline(DeadlineDescription received, Consumer<Deadline> onDeadlineComputed) {
				onDeadlineComputed.accept(deadline);
			}

			@Override
			public void close() {}
		};

		Consumer<Deadline> onDeadlineReceived = received -> {
			if (deadline.equals(received))
				semaphore.release();
		};

		try (var requester = new TestServer(8025, onDeadlineReceived); var service = MinerServices.open(miner, new URI("ws://localhost:8025"))) {
			requester.requestDeadline(description);
			assertTrue(semaphore.tryAcquire(1, 1, TimeUnit.SECONDS));
		}
	}

	@Test
	@DisplayName("if the miner sends a deadline after a delayed one, it gets forwarded to the requester")
	public void minerForwardsToRequesterAfterDelay() throws DeploymentException, IOException, URISyntaxException, InterruptedException, TimeoutException {
		var semaphore = new Semaphore(0);
		var shabal256 = shabal256(Function.identity());
		var description1 = DeadlineDescriptions.of(42, new byte[] { 1, 2, 3, 4, 5, 6 }, shabal256);
		var description2 = DeadlineDescriptions.of(43, new byte[] { 1, 2, 3, 4, 5, 6 }, shabal256);
		var deadline1 = Deadlines.of(new byte[] { 13, 42, 17, 19 }, 42L, new byte[] { 1, 2, 3, 4, 5, 6 }, 11, new byte[] { 1, 2, 3 }, shabal256);
		var deadline2 = Deadlines.of(new byte[] { 13, 42, 17, 19 }, 43L, new byte[] { 1, 2, 3, 4, 5, 6 }, 11, new byte[] { 1, 2, 3 }, shabal256);
		var delay = 2000L;

		var miner = new Miner() {

			@Override
			public void requestDeadline(DeadlineDescription received, Consumer<Deadline> onDeadlineComputed) {
				if (received.equals(description1))
					onDeadlineComputed.accept(deadline1);
				else
					onDeadlineComputed.accept(deadline2);
			}

			@Override
			public void close() {}
		};

		Consumer<Deadline> onDeadlineReceived = received -> {
			if (deadline2.equals(received))
				semaphore.release();
			else {
				try {
					Thread.sleep(delay);
				}
				catch (InterruptedException e) {}
			}
		};

		try (var requester = new TestServer(8025, onDeadlineReceived); var service = MinerServices.open(miner, new URI("ws://localhost:8025"))) {
			requester.requestDeadline(description1); // the call-back hangs for some time, then it works
			requester.requestDeadline(description2); // this works after the delay
			assertTrue(semaphore.tryAcquire(1, 2 * delay, TimeUnit.MILLISECONDS));
		}
	}

	@Test
	@DisplayName("a deadline sent back after the requester disconnects is simply lost, without errors")
	public void ifMinerSendsDeadlineAfterDisconnectionItIsIgnored() throws DeploymentException, IOException, URISyntaxException, InterruptedException, TimeoutException {
		var shabal256 = shabal256(Function.identity());
		var description = DeadlineDescriptions.of(42, new byte[] { 1, 2, 3, 4, 5, 6 }, shabal256);
		var deadline = Deadlines.of(new byte[] { 13, 42, 17, 19 }, 42L, new byte[] { 1, 2, 3, 4, 5, 6 }, 11, new byte[] { 1, 2, 3 }, shabal256);
		long delay = 2000;

		var miner = new Miner() {

			@Override
			public void requestDeadline(DeadlineDescription received, Consumer<Deadline> onDeadlineComputed) {
				try {
					Thread.sleep(delay);
				}
				catch (InterruptedException e) {}

				// when this is called, the requester has been already closed: the deadline is ignored
				onDeadlineComputed.accept(deadline);
			}

			@Override
			public void close() {}
		};

		// the deadline is not sent back to the closed requester, so no exception actually occurs
		try (var requester = new TestServer(8025, _deadline -> { throw new IllegalStateException("unexpected"); }); var service = MinerServices.open(miner, new URI("ws://localhost:8025"))) {
			requester.requestDeadline(description);
			Thread.sleep(delay / 4);
			requester.close();
			Thread.sleep(delay * 2);
		}
	}

	static {
		String current = System.getProperty("java.util.logging.config.file");
		if (current == null) {
			// if the property is not set, we provide a default (if it exists)
			URL resource = MinerServiceTests.class.getClassLoader().getResource("logging.properties");
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