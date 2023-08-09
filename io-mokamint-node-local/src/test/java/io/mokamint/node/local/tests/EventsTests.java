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

package io.mokamint.node.local.tests;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Path;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.concurrent.Semaphore;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.logging.LogManager;
import java.util.stream.Stream;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

import io.hotmoka.crypto.HashingAlgorithms;
import io.mokamint.application.api.Application;
import io.mokamint.miner.api.Miner;
import io.mokamint.node.api.DatabaseException;
import io.mokamint.node.api.NonGenesisBlock;
import io.mokamint.node.local.AlreadyInitializedException;
import io.mokamint.node.local.Config;
import io.mokamint.node.local.internal.LocalNodeImpl;
import io.mokamint.node.local.internal.blockchain.MineNewBlockTask.BlockMinedEvent;
import io.mokamint.node.local.internal.blockchain.MineNewBlockTask.IllegalDeadlineEvent;
import io.mokamint.node.local.internal.blockchain.MineNewBlockTask.NoDeadlineFoundEvent;
import io.mokamint.node.local.internal.blockchain.MineNewBlockTask.NoMinersAvailableEvent;
import io.mokamint.nonce.api.Deadline;
import io.mokamint.nonce.api.DeadlineDescription;

public class EventsTests {

	/**
	 * The application of the node used for testing.
	 */
	private static Application app;

	@BeforeAll
	public static void beforeAll() {
		app = mock(Application.class);
		when(app.prologIsValid(any())).thenReturn(true);
	}

	private static Config mkConfig(Path dir) throws NoSuchAlgorithmException {
		return Config.Builder.defaults()
			.setDir(dir)
			.setDeadlineWaitTimeout(1000) // a short time is OK for testing
			.build();
	}

	@Test
	@DisplayName("if a deadline is requested and a miner produces a valid deadline, a block is discovered")
	@Timeout(1)
	public void discoverNewBlockAfterDeadlineRequestToMiner(@TempDir Path dir) throws InterruptedException, NoSuchAlgorithmException, IOException, URISyntaxException, DatabaseException, AlreadyInitializedException {
		var semaphore = new Semaphore(0);
		var deadlineValue = new byte[] { 0, 0, 0, 0, 1, 0, 0, 0 };
		var deadlineProlog = new byte[] { 1, 2, 3, 4 };

		var myMiner = new Miner() {

			@Override
			public void requestDeadline(DeadlineDescription description, Consumer<Deadline> onDeadlineComputed) {
				Deadline deadline = mock(Deadline.class);
				when(deadline.isValid()).thenReturn(true);
				when(deadline.getProlog()).thenReturn(deadlineProlog);
				when(deadline.getData()).thenReturn(description.getData());
				when(deadline.getScoopNumber()).thenReturn(description.getScoopNumber());
				when(deadline.getValue()).thenReturn(deadlineValue);
				when(deadline.getHashing()).thenReturn(description.getHashing());
				when(deadline.matches(description)).thenReturn(true);

				onDeadlineComputed.accept(deadline);
			}

			@Override
			public void close() {}
		};

		class MyLocalNode extends LocalNodeImpl {

			private MyLocalNode() throws NoSuchAlgorithmException, IOException, DatabaseException, URISyntaxException, InterruptedException, AlreadyInitializedException {
				super(mkConfig(dir), app, true, myMiner);
			}

			@Override
			protected void onSubmit(Event event) {
				if (event instanceof BlockMinedEvent bde) {
					var block = bde.block;
					if (block instanceof NonGenesisBlock ngb && Arrays.equals(ngb.getDeadline().getValue(), deadlineValue))
						semaphore.release();
				}
					
				super.onSubmit(event);
			}
		}

		try (var node = new MyLocalNode()) {
			semaphore.acquire();
		}
	}

	@Test
	@DisplayName("if a deadline is requested and a miner produces an invalid deadline, the misbehavior is signalled to the node")
	@Timeout(1)
	public void signalIfInvalidDeadline(@TempDir Path dir) throws InterruptedException, NoSuchAlgorithmException, IOException, URISyntaxException, DatabaseException, AlreadyInitializedException {
		var semaphore = new Semaphore(0);
		var deadlineValue = new byte[] { 0, 0, 0, 0, 1, 0, 0, 0 };
	
		var myMiner = new Miner() {
	
			@Override
			public void requestDeadline(DeadlineDescription description, Consumer<Deadline> onDeadlineComputed) {
				Deadline deadline = mock(Deadline.class);
				when(deadline.isValid()).thenReturn(false); // <--
				when(deadline.getScoopNumber()).thenReturn(description.getScoopNumber());
				when(deadline.getData()).thenReturn(description.getData());
				when(deadline.getValue()).thenReturn(deadlineValue);
				when(deadline.getHashing()).thenReturn(description.getHashing());

				onDeadlineComputed.accept(deadline);
			}

			@Override
			public void close() {}
		};
	
		class MyLocalNode extends LocalNodeImpl {
	
			private MyLocalNode() throws NoSuchAlgorithmException, IOException, DatabaseException, URISyntaxException, InterruptedException, AlreadyInitializedException {
				super(mkConfig(dir), app, true, myMiner);
			}
	
			@Override
			protected void onSubmit(Event event) {
				if (event instanceof IllegalDeadlineEvent ide && ide.miner == myMiner)
					semaphore.release();
					
				super.onSubmit(event);
			}
		}
	
		try (var node = new MyLocalNode()) {
			semaphore.acquire();
		}
	}

	@Test
	@DisplayName("if a node has no miners, an event is signalled")
	@Timeout(1)
	public void signalIfNoMiners(@TempDir Path dir) throws InterruptedException, NoSuchAlgorithmException, IOException, DatabaseException, URISyntaxException, AlreadyInitializedException {
		var semaphore = new Semaphore(0);

		class MyLocalNode extends LocalNodeImpl {

			public MyLocalNode() throws NoSuchAlgorithmException, IOException, DatabaseException, URISyntaxException, InterruptedException, AlreadyInitializedException {
				super(mkConfig(dir), app, true, new Miner[0]);
			}

			@Override
			protected void onSubmit(Event event) {
				if (event instanceof NoMinersAvailableEvent)
					semaphore.release();
					
				super.onSubmit(event);
			}
		}

		try (var node = new MyLocalNode()) {
			semaphore.acquire();
		}
	}

	@Test
	@DisplayName("if miners do not produce any deadline, an event is signalled to the node")
	@Timeout(3) // three times config.deadlineWaitTimeout
	public void signalIfNoDeadlineArrives(@TempDir Path dir) throws InterruptedException, NoSuchAlgorithmException, IOException, URISyntaxException, DatabaseException, AlreadyInitializedException {
		var semaphore = new Semaphore(0);
		var myMiner = mock(Miner.class);

		class MyLocalNode extends LocalNodeImpl {

			private MyLocalNode() throws NoSuchAlgorithmException, DatabaseException, IOException, URISyntaxException, InterruptedException, AlreadyInitializedException {
				super(mkConfig(dir), app, true, myMiner);
			}

			@Override
			protected void onSubmit(Event event) {
				if (event instanceof NoDeadlineFoundEvent)
					semaphore.release();
					
				super.onSubmit(event);
			}
		}

		try (var node = new MyLocalNode()) {
			semaphore.acquire();
		}
	}

	@Test
	@DisplayName("if a miner provides deadlines for the wrong hashing algorithm, an event is signalled to the node")
	@Timeout(1)
	public void signalIfDeadlineForWrongAlgorithmArrives(@TempDir Path dir) throws InterruptedException, NoSuchAlgorithmException, IOException, URISyntaxException, DatabaseException, AlreadyInitializedException {
		var semaphore = new Semaphore(0);
		var deadlineValue = new byte[] { 0, 0, 0, 0, 1, 0, 0, 0 };
		var config = mkConfig(dir);

		// we look for a hashing algorithm different from that expected by the node
		String algoName = Stream.of(HashingAlgorithms.TYPES.values())
			.map(Enum::name)
			.map(String::toLowerCase)
			.filter(name -> !name.equals(config.getHashingForDeadlines().getName()))
			.findAny()
			.get();

		var algo = HashingAlgorithms.mk(algoName, Function.identity());

		var myMiner = new Miner() {

			@Override
			public void requestDeadline(DeadlineDescription description, Consumer<Deadline> onDeadlineComputed) {
				Deadline deadline = mock(Deadline.class);
				when(deadline.isValid()).thenReturn(true);
				when(deadline.getScoopNumber()).thenReturn(description.getScoopNumber());
				when(deadline.getData()).thenReturn(description.getData());
				when(deadline.getValue()).thenReturn(deadlineValue);
				when(deadline.getHashing()).thenReturn(algo);

				onDeadlineComputed.accept(deadline);
			}

			@Override
			public void close() {}
		};

		class MyLocalNode extends LocalNodeImpl {

			private MyLocalNode() throws NoSuchAlgorithmException, DatabaseException, IOException, URISyntaxException, InterruptedException, AlreadyInitializedException {
				super(config, app, true, myMiner);
			}

			@Override
			protected void onSubmit(Event event) {
				if (event instanceof IllegalDeadlineEvent)
					semaphore.release();

				super.onSubmit(event);
			}
		}

		try (var node = new MyLocalNode()) {
			semaphore.acquire();
		}
	}

	static {
		String current = System.getProperty("java.util.logging.config.file");
		if (current == null) {
			// if the property is not set, we provide a default (if it exists)
			URL resource = EventsTests.class.getClassLoader().getResource("logging.properties");
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