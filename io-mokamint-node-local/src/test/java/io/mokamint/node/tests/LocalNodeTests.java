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

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.net.URL;
import java.util.Arrays;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeoutException;
import java.util.function.BiConsumer;
import java.util.logging.LogManager;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import io.mokamint.application.api.Application;
import io.mokamint.miner.api.Miner;
import io.mokamint.node.local.internal.LocalNodeImpl;
import io.mokamint.node.local.internal.blockchain.NonGenesisBlock;
import io.mokamint.nonce.api.Deadline;

public class LocalNodeTests {

	@Test
	@DisplayName("if a deadline is requested and a miner produces a valid deadline, a block is discovered")
	@Timeout(1)
	public void discoverNewBlockAfterDeadlineRequestToMiner() throws InterruptedException {
		var semaphore = new Semaphore(0);
		var deadlineValue = new byte[] { 0, 0, 0, 0, 1, 0, 0, 0 };

		var myMiner = new Miner() {

			@Override
			public void requestDeadline(int scoopNumber, byte[] data, BiConsumer<Deadline, Miner> onDeadlineComputed) {
				Deadline deadline = mock(Deadline.class);
				when(deadline.isValid()).thenReturn(true);
				when(deadline.getScoopNumber()).thenReturn(scoopNumber);
				when(deadline.getData()).thenReturn(data);
				when(deadline.getValue()).thenReturn(deadlineValue);
				onDeadlineComputed.accept(deadline, this);
			}

			@Override
			public void close() {}
		};

		class MyLocalNode extends LocalNodeImpl {

			private MyLocalNode() {
				super(getTestApplication(), myMiner);
			}

			@Override
			public void signal(Event event) {
				if (event instanceof BlockDiscoveryEvent) {
					var block = ((BlockDiscoveryEvent) event).block;
					if (block instanceof NonGenesisBlock && Arrays.equals(((NonGenesisBlock) block).getDeadline().getValue(), deadlineValue)) {
						semaphore.release();
					}
				}
					
				super.signal(event);
			}
		}

		try (var node = new MyLocalNode()) {
			semaphore.acquire();
		}
	}

	@Test
	@DisplayName("if a deadline is requested and a miner produces a invalid deadline, the misbahavior is signalled to the node")
	@Timeout(1)
	public void signalIfInvalidDeadline() throws InterruptedException {
		var semaphore = new Semaphore(0);
		var deadlineValue = new byte[] { 0, 0, 0, 0, 1, 0, 0, 0 };
	
		var myMiner = new Miner() {
	
			@Override
			public void requestDeadline(int scoopNumber, byte[] data, BiConsumer<Deadline, Miner> onDeadlineComputed) {
				Deadline deadline = mock(Deadline.class);
				when(deadline.isValid()).thenReturn(false); // <--
				when(deadline.getScoopNumber()).thenReturn(scoopNumber);
				when(deadline.getData()).thenReturn(data);
				when(deadline.getValue()).thenReturn(deadlineValue);
				onDeadlineComputed.accept(deadline, this);
			}
	
			@Override
			public void close() {}
		};
	
		class MyLocalNode extends LocalNodeImpl {
	
			private MyLocalNode() {
				super(getTestApplication(), myMiner);
			}
	
			@Override
			public void signal(Event event) {
				if (event instanceof IllegalDeadlineEvent) {
					if (((IllegalDeadlineEvent) event).miner == myMiner)
						semaphore.release();
				}
					
				super.signal(event);
			}
		}
	
		try (var node = new MyLocalNode()) {
			semaphore.acquire();
		}
	}

	@Test
	@DisplayName("if a node has no miners, an event is signalled")
	@Timeout(1)
	public void signalIfNoMiners() throws InterruptedException {
		var semaphore = new Semaphore(0);

		class MyLocalNode extends LocalNodeImpl {

			public MyLocalNode() {
				super(getTestApplication(), new Miner[0]);
			}

			@Override
			public void signal(Event event) {
				if (event instanceof NoMinersAvailableEvent)
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
	public void signalIfMinerTimeouts() throws InterruptedException, RejectedExecutionException, TimeoutException {
		var semaphore = new Semaphore(0);

		var myMiner = mock(Miner.class);
		doThrow(TimeoutException.class).when(myMiner).requestDeadline(anyInt(), any(), any());

		class MyLocalNode extends LocalNodeImpl {

			private MyLocalNode() {
				super(getTestApplication(), myMiner);
			}

			@Override
			public void signal(Event event) {
				if (event instanceof MinerTimeoutEvent && ((MinerTimeoutEvent) event).miner == myMiner)
					semaphore.release();
					
				super.signal(event);
			}
		}

		try (var node = new MyLocalNode()) {
			semaphore.acquire();
		}
	}

	@Test
	@DisplayName("if miners do not produce any deadline, an event is signalled to the node")
	@Timeout(3) // TODO
	public void signalIfNoDeadlineArrives() throws InterruptedException {
		var semaphore = new Semaphore(0);
		var myMiner = mock(Miner.class);

		class MyLocalNode extends LocalNodeImpl {

			private MyLocalNode() {
				super(getTestApplication(), myMiner);
			}

			@Override
			public void signal(Event event) {
				if (event instanceof NoDeadlineFoundEvent)
					semaphore.release();
					
				super.signal(event);
			}
		}

		try (var node = new MyLocalNode()) {
			semaphore.acquire();
		}
	}

	private Application getTestApplication() {
		Application app = mock(Application.class);
		when(app.prologIsValid(any())).thenReturn(true);
		return app;
	}

	static {
		String current = System.getProperty("java.util.logging.config.file");
		if (current == null) {
			// if the property is not set, we provide a default (if it exists)
			URL resource = LocalNodeTests.class.getClassLoader().getResource("logging.properties");
			if (resource != null)
				try {
					LogManager.getLogManager().readConfiguration(resource.openStream());
				}
				catch (SecurityException | IOException e) {
					throw new RuntimeException("Cannot load logging.properties file", e);
				}
		}
	}
}