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

import java.io.IOException;
import java.net.URL;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeoutException;
import java.util.function.BiConsumer;
import java.util.logging.LogManager;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import io.mokamint.application.api.Application;
import io.mokamint.miner.api.Miner;
import io.mokamint.node.local.Main;
import io.mokamint.node.local.internal.LocalNodeImpl;
import io.mokamint.nonce.api.Deadline;

/**
 * Tests for local nodes.
 */
public class LocalNodeTests {

	static {
		String current = System.getProperty("java.util.logging.config.file");
		if (current == null) {
			// if the property is not set, we provide a default (if it exists)
			URL resource = Main.class.getClassLoader().getResource("logging.properties");
			if (resource != null)
				try {
					LogManager.getLogManager().readConfiguration(resource.openStream());
				}
				catch (SecurityException | IOException e) {
					throw new RuntimeException("Cannot load logging.properties file", e);
				}
		}
	}

	@Test
	@DisplayName("if a node has no miners, an event is signalled")
	@Timeout(1)
	public void signalIfNoMiners() throws InterruptedException {
		var semaphore = new Semaphore(0);

		class MyLocalNode extends LocalNodeImpl {

			public MyLocalNode() {
				super(new TestApplication(), new Miner[0]);
			}

			@Override
			public void signal(Event event) {
				if (event instanceof LocalNodeImpl.NoMinersAvailableEvent)
					semaphore.release();
					
				super.signal(event);
			}
		}

		try (var node = new MyLocalNode()) {
			semaphore.acquire();
		}
	}

	@Test
	@DisplayName("if a miner timeouts, an event is signalled")
	@Timeout(1)
	public void signalIfMinerTimeouts() throws InterruptedException {
		var semaphore = new Semaphore(0);

		var myMiner = new Miner() {

			@Override
			public void requestDeadline(int scoopNumber, byte[] data, BiConsumer<Deadline, Miner> onDeadlineComputed) throws TimeoutException {
				throw new TimeoutException();
			}

			@Override
			public void close() {
			}
		};

		class MyLocalNode extends LocalNodeImpl {

			public MyLocalNode() {
				super(new TestApplication(), myMiner);
			}

			@Override
			public void signal(Event event) {
				if (event instanceof LocalNodeImpl.MinerTimeoutEvent && ((LocalNodeImpl.MinerTimeoutEvent) event).miner == myMiner)
					semaphore.release();
					
				super.signal(event);
			}
		}

		try (var node = new MyLocalNode()) {
			semaphore.acquire();
		}
	}

	private static class TestApplication implements Application {

		@Override
		public boolean prologIsValid(byte[] prolog) {
			return true;
		}
	}
}
