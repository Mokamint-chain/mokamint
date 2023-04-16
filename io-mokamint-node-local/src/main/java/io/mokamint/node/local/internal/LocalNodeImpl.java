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

package io.mokamint.node.local.internal;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import io.mokamint.application.api.Application;
import io.mokamint.miner.api.Miner;
import io.mokamint.node.api.Node;
import io.mokamint.node.local.internal.blockchain.Block;
import io.mokamint.node.local.internal.tasks.DiscoverNewBlockTask;

/**
 * A local node of a Mokamint blockchain.
 */
public class LocalNodeImpl implements Node {

	/**
	 * The application executed by the blockchain.
	 */
	private final Application app;

	/**
	 * The miners connected to the node.
	 */
	//@GuardeBy(itself)
	private final Set<Miner> miners;

	private final ExecutorService events = Executors.newSingleThreadExecutor();

	private final ExecutorService workers = Executors.newCachedThreadPool();

	private final static Logger LOGGER = Logger.getLogger(LocalNodeImpl.class.getName());

	/**
	 * Creates a local node of a Mokamint blockchain, for the given application,
	 * using the given miners.
	 *
	 * @param app the application
	 * @param miners the miners
	 */
	public LocalNodeImpl(Application app, Miner... miners) {
		this.app = app;
		this.miners = Stream.of(miners).collect(Collectors.toSet());

		Block head = Block.genesis(LocalDateTime.now(ZoneId.of("UTC")));
		signal(new BlockDiscoveredEvent(head));
	}

	@Override
	public void close() {
		events.shutdown();
		workers.shutdown();
		
		try {
			events.awaitTermination(10, TimeUnit.SECONDS);
			workers.awaitTermination(10, TimeUnit.SECONDS);
		}
		catch (InterruptedException e) {
			throw new RuntimeException(e);
		}
	}

	public Application getApplication() {
		return app;
	}

	public boolean hasMiner(Miner miner) {
		synchronized (miners) {
			return miners.contains(miner);
		}
	}

	public void forEachMiner(Consumer<Miner> what) {
		// it's OK to be weakly consistent
		Set<Miner> copy;
		synchronized (miners) {
			copy = new HashSet<>(miners);
		}

		copy.forEach(what);
	}

	public void signal(Event event) {
		try {
			LOGGER.info("received " + event);
			events.execute(event);
		}
		catch (RejectedExecutionException e) {
			LOGGER.info(event + " rejected because the node is shutting down");
		}
	}

	private void execute(Task task) {
		try {
			workers.execute(task);
		}
		catch (RejectedExecutionException e) {
			LOGGER.info("task rejected because the node is shutting down");
		}
	}

	public class BlockDiscoveredEvent implements Event {
		private final Block block;

		public BlockDiscoveredEvent(Block block) {
			this.block = block;
		}

		@Override
		public String toString() {
			return "block discovery event for block number " + block.getNumber();
		}

		@Override
		public void run() {
			execute(new DiscoverNewBlockTask(LocalNodeImpl.this, block));
		}
	}

	public class MinerMisbehaviorEvent implements Event {
		private final Miner miner;

		public MinerMisbehaviorEvent(Miner miner) {
			this.miner = miner;
		}

		@Override
		public String toString() {
			return "miner misbehavior event for miner " + miner.toString();
		}

		@Override
		public void run() {
			synchronized (miners) {
				miners.remove(miner);
			}
		}
	}

	private interface Event extends Runnable {
	}

	public abstract class Task implements Runnable {
		protected LocalNodeImpl getNode() {
			return LocalNodeImpl.this;
		}
	}
}