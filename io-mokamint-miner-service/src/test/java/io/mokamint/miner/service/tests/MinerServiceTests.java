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

import static io.hotmoka.crypto.HashingAlgorithms.sha256;
import static io.hotmoka.crypto.HashingAlgorithms.shabal256;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.URI;
import java.security.NoSuchAlgorithmException;
import java.util.UUID;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import io.hotmoka.crypto.HashingAlgorithms;
import io.hotmoka.crypto.SignatureAlgorithms;
import io.hotmoka.crypto.api.HashingAlgorithm;
import io.hotmoka.testing.AbstractLoggedTests;
import io.mokamint.miner.MiningSpecifications;
import io.mokamint.miner.api.Miner;
import io.mokamint.miner.api.MiningSpecification;
import io.mokamint.miner.service.MinerServices;
import io.mokamint.nonce.Challenges;
import io.mokamint.nonce.Deadlines;
import io.mokamint.nonce.Prologs;
import io.mokamint.nonce.api.Challenge;
import io.mokamint.nonce.api.Deadline;

public class MinerServiceTests extends AbstractLoggedTests {

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
	@DisplayName("if a deadline description is requested to a miner service, it gets forwarded to the adapted miner")
	public void minerServiceForwardsToMiner() throws Exception {
		var semaphore = new Semaphore(0);
		var hashingForGenerations = sha256();
		var generationSignature = new byte[hashingForGenerations.length()];
		for (int pos = 0; pos < generationSignature.length; pos++)
			generationSignature[pos] = (byte) (42 + pos);
		var description = Challenges.of(42, generationSignature, shabal256(), hashingForGenerations);

		var miner = new Miner() {

			@Override
			public void requestDeadline(Challenge received, Consumer<Deadline> onDeadlineComputed) {
				if (description.equals(received))
					semaphore.release();
			}

			@Override
			public UUID getUUID() {
				return UUID.randomUUID();
			}

			@Override
			public MiningSpecification getMiningSpecification() {
				return MINING_SPECIFICATION;
			}

			@Override
			public void close() {}
		};

		try (var requester = new TestServer(8025, deadline -> {}); var service = MinerServices.of(miner, new URI("ws://localhost:8025"), 30_000)) {
			requester.requestDeadline(description);
			assertTrue(semaphore.tryAcquire(1, 1, TimeUnit.SECONDS));
		}
	}

	@Test
	@DisplayName("if the miner sends a deadline, it gets forwarded to the requester")
	public void minerForwardsToRequester() throws Exception {
		var semaphore = new Semaphore(0);
		HashingAlgorithm shabal256 = shabal256();
		var hashingForGenerations = sha256();
		var generationSignature = new byte[hashingForGenerations.length()];
		for (int pos = 0; pos < generationSignature.length; pos++)
			generationSignature[pos] = (byte) (42 + pos);
		var ed25519 = SignatureAlgorithms.ed25519();
		var plotKeys = ed25519.getKeyPair();
		var prolog = Prologs.of("octopus", ed25519, ed25519.getKeyPair().getPublic(), ed25519, plotKeys.getPublic(), new byte[0]);
		var challenge = Challenges.of(42, generationSignature, shabal256, hashingForGenerations);
		var value = new byte[shabal256.length()];
		for (int pos = 0; pos < value.length; pos++)
			value[pos] = (byte) pos;
		var deadline = Deadlines.of(prolog, 42L, value, challenge, plotKeys.getPrivate());

		var miner = new Miner() {

			@Override
			public void requestDeadline(Challenge received, Consumer<Deadline> onDeadlineComputed) {
				onDeadlineComputed.accept(deadline);
			}

			@Override
			public UUID getUUID() {
				return UUID.randomUUID();
			}

			@Override
			public MiningSpecification getMiningSpecification() {
				return MINING_SPECIFICATION;
			}

			@Override
			public void close() {}
		};

		Consumer<Deadline> onDeadlineReceived = received -> {
			if (deadline.equals(received))
				semaphore.release();
		};

		try (var requester = new TestServer(8025, onDeadlineReceived); var service = MinerServices.of(miner, new URI("ws://localhost:8025"), 30_000)) {
			requester.requestDeadline(challenge);
			assertTrue(semaphore.tryAcquire(1, 1, TimeUnit.SECONDS));
		}
	}

	@Test
	@DisplayName("if the miner sends a deadline after a delayed one, it gets forwarded to the requester")
	public void minerForwardsToRequesterAfterDelay() throws Exception {
		var semaphore = new Semaphore(0);
		HashingAlgorithm shabal256 = shabal256();
		var hashingForGenerations = HashingAlgorithms.sha256();
		var generationSignature = new byte[hashingForGenerations.length()];
		for (int pos = 0; pos < generationSignature.length; pos++)
			generationSignature[pos] = (byte) (42 + pos);
		var challenge1 = Challenges.of(42, generationSignature, shabal256, hashingForGenerations);
		var challenge2 = Challenges.of(43, generationSignature, shabal256, hashingForGenerations);
		var value = new byte[shabal256.length()];
		for (int pos = 0; pos < value.length; pos++)
			value[pos] = (byte) pos;
		var ed25519 = SignatureAlgorithms.ed25519();
		var plotKeys = ed25519.getKeyPair();
		var prolog = Prologs.of("octopus", ed25519, ed25519.getKeyPair().getPublic(), ed25519, plotKeys.getPublic(), new byte[0]);
		var deadline1 = Deadlines.of(prolog, 42L, value, challenge1, plotKeys.getPrivate());
		var deadline2 = Deadlines.of(prolog, 43L, value, challenge2, plotKeys.getPrivate());
		var delay = 2000L;

		var miner = new Miner() {

			@Override
			public void requestDeadline(Challenge received, Consumer<Deadline> onDeadlineComputed) {
				if (received.equals(challenge1))
					onDeadlineComputed.accept(deadline1);
				else
					onDeadlineComputed.accept(deadline2);
			}

			@Override
			public UUID getUUID() {
				return UUID.randomUUID();
			}

			@Override
			public MiningSpecification getMiningSpecification() {
				return MINING_SPECIFICATION;
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
				catch (InterruptedException e) {
					Thread.currentThread().interrupt();
				}
			}
		};

		try (var requester = new TestServer(8025, onDeadlineReceived); var service = MinerServices.of(miner, new URI("ws://localhost:8025"), 30_000)) {
			requester.requestDeadline(challenge1); // the call-back hangs for some time, then it works
			requester.requestDeadline(challenge2); // this works after the delay
			assertTrue(semaphore.tryAcquire(1, 2 * delay, TimeUnit.MILLISECONDS));
		}
	}

	@Test
	@DisplayName("a deadline sent back after the requester disconnects is simply lost, without errors")
	public void ifMinerSendsDeadlineAfterDisconnectionItIsIgnored() throws Exception {
		HashingAlgorithm shabal256 = shabal256();
		var hashingForGenerations = sha256();
		var generationSignature = new byte[hashingForGenerations.length()];
		for (int pos = 0; pos < generationSignature.length; pos++)
			generationSignature[pos] = (byte) (42 + pos);
		var challenge = Challenges.of(42, generationSignature, shabal256, hashingForGenerations);
		var value = new byte[shabal256.length()];
		for (int pos = 0; pos < value.length; pos++)
			value[pos] = (byte) pos;
		var ed25519 = SignatureAlgorithms.ed25519();
		var plotKeys = ed25519.getKeyPair();
		var prolog = Prologs.of("octopus", ed25519, ed25519.getKeyPair().getPublic(), ed25519, plotKeys.getPublic(), new byte[0]);
		var deadline = Deadlines.of(prolog, 42L, value, challenge, plotKeys.getPrivate());
		long delay = 2000;

		var miner = new Miner() {

			@Override
			public void requestDeadline(Challenge received, Consumer<Deadline> onDeadlineComputed) {
				try {
					Thread.sleep(delay);
				}
				catch (InterruptedException e) {
					Thread.currentThread().interrupt();
				}

				// when this is called, the requester has been already closed: the deadline is ignored
				onDeadlineComputed.accept(deadline);
			}

			@Override
			public UUID getUUID() {
				return UUID.randomUUID();
			}

			@Override
			public MiningSpecification getMiningSpecification() {
				return MINING_SPECIFICATION;
			}

			@Override
			public void close() {}
		};

		// the deadline is not sent back to the closed requester, so no exception actually occurs
		try (var requester = new TestServer(8025, _deadline -> { throw new IllegalStateException("unexpected"); }); var service = MinerServices.of(miner, URI.create("ws://localhost:8025"), 30_000)) {
			requester.requestDeadline(challenge);
			Thread.sleep(delay / 4);
			requester.close();
			Thread.sleep(delay * 2);
		}
	}
}