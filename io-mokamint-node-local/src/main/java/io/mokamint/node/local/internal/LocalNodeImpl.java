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

import java.io.IOException;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Predicate;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Stream;

import io.hotmoka.annotations.OnThread;
import io.hotmoka.annotations.ThreadSafe;
import io.mokamint.application.api.Application;
import io.mokamint.miner.api.Miner;
import io.mokamint.node.ChainInfos;
import io.mokamint.node.NodeInfos;
import io.mokamint.node.Versions;
import io.mokamint.node.api.Block;
import io.mokamint.node.api.ChainInfo;
import io.mokamint.node.api.ClosedNodeException;
import io.mokamint.node.api.DatabaseException;
import io.mokamint.node.api.IncompatiblePeerException;
import io.mokamint.node.api.NodeInfo;
import io.mokamint.node.api.Peer;
import io.mokamint.node.api.PeerInfo;
import io.mokamint.node.api.Version;
import io.mokamint.node.local.Config;
import io.mokamint.node.local.LocalNode;
import io.mokamint.node.local.internal.blockchain.Blockchain;
import io.mokamint.node.messages.MessageMemories;
import io.mokamint.node.messages.MessageMemory;
import io.mokamint.node.messages.api.WhisperPeersMessage;
import io.mokamint.node.messages.api.Whisperer;

/**
 * A local node of a Mokamint blockchain.
 */
@ThreadSafe
public class LocalNodeImpl implements LocalNode {

	/**
	 * The configuration of the node.
	 */
	private final Config config;

	/**
	 * The miners connected to the node.
	 */
	private final NodeMiners miners;

	/**
	 * The peers of the node.
	 */
	private final NodePeers peers;

	/**
	 * The blockchain of this node.
	 */
	private final Blockchain blockchain;

	/**
	 * The database containing the blockchain.
	 */
	private final Database db;

	/**
	 * The version of this node.
	 */
	private final Version version;

	/**
	 * The UUID of this node.
	 */
	private final UUID uuid;

	/**
	 * The single executor of the events. Events get queued into this queue and run in order
	 * on that executor, called the event thread.
	 */
	private final ExecutorService events = Executors.newSingleThreadExecutor();

	/**
	 * The executor of tasks. There might be more tasks in execution at the same time.
	 */
	private final ExecutorService tasks = Executors.newCachedThreadPool();

	/**
	 * The executor of periodic tasks. There might be more periodic tasks in execution
	 * at the same time.
	 */
	private final ScheduledExecutorService periodicTasks = Executors.newScheduledThreadPool(5);

	/**
	 * The code to execute when this node gets closed.
	 */
	private final CopyOnWriteArrayList<CloseHandler> onCloseHandlers = new CopyOnWriteArrayList<>();

	/**
	 * The whisperers bound to this node.
	 */
	private final CopyOnWriteArrayList<Whisperer> boundWhisperers = new CopyOnWriteArrayList<>();

	/**
	 * A memory of the last whispered messages,
	 * This is used to avoid whispering already whispered messages again.
	 */
	private final MessageMemory whisperedMessages;

	/**
	 * True if and only if this node has been closed already.
	 */
	private final AtomicBoolean isClosed = new AtomicBoolean();

	private final static Logger LOGGER = Logger.getLogger(LocalNodeImpl.class.getName());

	/**
	 * Creates a local node of a Mokamint blockchain, for the given application,
	 * using the given miners.
	 * 
	 * @param config the configuration of the node
	 * @param app the application
	 * @param forceMining performs mining also if the node cannot be synchronized to a recent head;
	 *                    this is useful to start a brand new blockchain, since in that case
	 *                    there is no other blockchain, in any peer, to synchronize to
	 * @param miners the miners
	 * @throws NoSuchAlgorithmException if some block in the database uses an unknown hashing algorithm
	 * @throws IOException if the version information cannot be read
	 * @throws DatabaseException if the database is corrupted
	 */
	public LocalNodeImpl(Config config, Application app, boolean forceMining, Miner... miners) throws NoSuchAlgorithmException, DatabaseException, IOException {
		this.config = config;
		this.db = new Database(config);
		this.version = Versions.current();
		this.uuid = db.getUUID();
		this.whisperedMessages = MessageMemories.of(config.whisperingMemorySize);
		this.miners = new NodeMiners(config, Stream.of(miners));
		this.peers = new NodePeers(this, db, this::submit, this::submit, this::submitWithFixedDelay);
		this.blockchain = new Blockchain(db, app, this.miners, this::submit, this::submit);

		if (forceMining)
			blockchain.mineNextBlock(blockchain.getHead());
	}

	@Override
	public void addOnClosedHandler(CloseHandler handler) {
		onCloseHandlers.add(handler);
	}

	@Override
	public void removeOnCloseHandler(CloseHandler handler) {
		onCloseHandlers.add(handler);
	}

	@Override
	public void bindWhisperer(Whisperer whisperer) {
		boundWhisperers.add(whisperer);
	}

	@Override
	public void unbindWhisperer(Whisperer whisperer) {
		boundWhisperers.remove(whisperer);
	}

	@Override
	public void whisper(WhisperPeersMessage message, Predicate<Whisperer> seen) {
		whisper(message, seen, true);
	}

	@Override
	public void whisperItself(WhisperPeersMessage message, Predicate<Whisperer> seen) {
		whisper(message, seen, false);
	}

	@Override
	public Optional<Block> getBlock(byte[] hash) throws DatabaseException, NoSuchAlgorithmException, ClosedNodeException {
		ensureIsOpen();
		return db.getBlock(hash);
	}

	@Override
	public Stream<PeerInfo> getPeerInfos() {
		return peers.get();
	}

	@Override
	public void addPeer(Peer peer) throws TimeoutException, InterruptedException, ClosedNodeException, IOException, IncompatiblePeerException, DatabaseException {
		ensureIsOpen();
		peers.add(peer, true);
	}

	@Override
	public void removePeer(Peer peer) throws DatabaseException, ClosedNodeException {
		ensureIsOpen();
		peers.remove(peer);
	}

	@Override
	public void close() throws InterruptedException, DatabaseException, IOException {
		if (!isClosed.getAndSet(true)) {
			events.shutdownNow();
			tasks.shutdownNow();
			periodicTasks.shutdownNow();

			InterruptedException interruptedException = null;
			
			for (var handler: onCloseHandlers) {
				try {
					handler.close();
				}
				catch (InterruptedException e) {
					interruptedException = e;
				}
			}

			try {
				events.awaitTermination(10, TimeUnit.SECONDS);
				tasks.awaitTermination(10, TimeUnit.SECONDS);
				periodicTasks.awaitTermination(10, TimeUnit.SECONDS);
			}
			finally {
				try {
					db.close();
				}
				finally {
					peers.close();
				}
			}

			if (interruptedException != null)
				throw interruptedException;
		}
	}

	@Override
	public NodeInfo getInfo() {
		return NodeInfos.of(version, uuid, LocalDateTime.now(ZoneId.of("UTC")));
	}

	/**
	 * Yields the configuration of this node.
	 * 
	 * @return the configuration of this node
	 */
	@Override
	public Config getConfig() {
		return config;
	}

	@Override
	public ChainInfo getChainInfo() throws NoSuchAlgorithmException, DatabaseException, ClosedNodeException {
		ensureIsOpen();
		var maybeHeadHash = db.getHeadHash();
		if (maybeHeadHash.isEmpty())
			return ChainInfos.of(0L, Optional.empty(), Optional.empty());
		else {
			var head = db.getBlock(maybeHeadHash.get()).orElseThrow(() -> new DatabaseException("the hash of the head is set but the head block is not in the database"));
			return ChainInfos.of(head.getHeight(), db.getGenesisHash(), maybeHeadHash);
		}
	}

	private void whisper(WhisperPeersMessage message, Predicate<Whisperer> seen, boolean tryToAddToThePeers) {
		if (seen.test(this) || !whisperedMessages.add(message))
			return;

		Predicate<Whisperer> newSeen = seen.or(Predicate.isEqual(this));
		peers.whisper(message, newSeen, tryToAddToThePeers);
		boundWhisperers.forEach(whisperer -> whisperer.whisper(message, newSeen));
	}

	private void ensureIsOpen() throws ClosedNodeException {
		if (isClosed.get())
			throw new ClosedNodeException("the node has been closed");
	}

	/**
	 * An event.
	 */
	public interface Event {

		/**
		 * Main body of the event execution.
		 * 
		 * @throws Exception if the execution fails
		 */
		@OnThread("events")
		void body() throws Exception;
	}

	/**
	 * Callback called when an event is submitted.
	 * It can be useful for testing or monitoring events.
	 * 
	 * @param event the event
	 */
	protected void onSubmit(Event event) {}

	/**
	 * Callback called when an event begins being executed.
	 * It can be useful for testing or monitoring events.
	 * 
	 * @param event the event
	 */
	protected void onStart(Event event) {}

	/**
	 * Callback called at the end of the successful execution of an event.
	 * It can be useful for testing or monitoring events.
	 * 
	 * @param event the event
	 */
	protected void onComplete(Event event) {}

	/**
	 * Callback called at the end of the failed execution of an event.
	 * It can be useful for testing or monitoring events.
	 * 
	 * @param event the event
	 * @param exception the failure cause
	 */
	protected void onFail(Event event, Exception e) {
		LOGGER.log(Level.SEVERE, "failed execution of " + event, e);
	}

	/**
	 * Signals that an event occurred. This is typically called from tasks,
	 * to signal that something occurred and that the node must react accordingly.
	 * Events get queued into the {@link #events} queue and eventually executed
	 * on its only thread, in order.
	 * 
	 * @param event the emitted event
	 */
	private void submit(Event event) {
		var runnable = new Runnable() {

			@Override @OnThread("events")
			public final void run() {
				onStart(event);

				try {
					event.body();
				}
				catch (Exception e) {
					onFail(event, e);
					return;
				}

				onComplete(event);
			}
		};

		try {
			LOGGER.info("received " + event);
			onSubmit(event);
			events.execute(runnable);
		}
		catch (RejectedExecutionException e) {
			LOGGER.warning(event + " rejected, probably because the node is shutting down");
		}
	}

	/**
	 * A task is a complex activity that can be run in its own thread. Once it completes,
	 * it typically fires some events to signal something to the node.
	 */
	public interface Task {

		/**
		 * Main body of the task execution.
		 * 
		 * @throws Exception if the execution fails
		 */
		@OnThread("tasks")
		void body() throws Exception;
	}

	/**
	 * Callback called when a task is submitted.
	 * It can be useful for testing or monitoring tasks.
	 * 
	 * @param task the task
	 */
	protected void onSubmit(Task task) {}

	/**
	 * Callback called when a task begins being executed.
	 * It can be useful for testing or monitoring events.
	 * 
	 * @param task the task
	 */
	protected void onStart(Task task) {}

	/**
	 * Callback called at the end of the successful execution of a task.
	 * It can be useful for testing or monitoring events.
	 * 
	 * @param task the task
	 */
	protected void onComplete(Task task) {}

	/**
	 * Callback called at the end of the failed execution of a task.
	 * It can be useful for testing or monitoring events.
	 * 
	 * @param task the task
	 * @param exception the failure cause
	 */
	protected void onFail(Task task, Exception e) {
		LOGGER.log(Level.SEVERE, "failed execution of " + task, e);
	}

	/**
	 * An adapter of a task into a runnable with logs.
	 */
	private class RunnableTask implements Runnable {
		private final Task task;

		private RunnableTask(Task task) {
			this.task = task;
		}

		@Override @OnThread("events")
		public final void run() {
			onStart(task);

			try {
				task.body();
			}
			catch (Exception e) {
				onFail(task, e);
				return;
			}

			onComplete(task);
		}
	};

	/**
	 * Runs the given task in one thread from the {@link #tasks} executor.
	 * 
	 * @param task the task to run
	 */
	private void submit(Task task) {
		try {
			LOGGER.info("scheduling " + task);
			onSubmit(task);
			tasks.execute(new RunnableTask(task));
		}
		catch (RejectedExecutionException e) {
			LOGGER.warning(task + " rejected, probably because the node is shutting down");
		}
	}

	/**
	 * A periodic spawner of a task.
	 */
	public interface TaskSpawnerWithFixedDelay {

		/**
		 * Spawns the given task after the {@code initialDelay} and then every {@code delay}.
		 * 
		 * @param task the task
		 * @param initialDelay the initial delay
		 * @param delay the periodic delay
		 * @param unit the time unit of the delays
		 */
		void spawnWithFixedDelay(Task task, long initialDelay, long delay, TimeUnit unit);
	}

	/**
	 * Runs the given task, periodically, with the {@link #periodicTasks} executor.
	 * 
	 * @param task the task to run
	 * @param initialDelay the time to wait before running the task
	 * @param delay the time interval between successive, iterated executions
	 * @param unit the time interval unit
	 */
	private void submitWithFixedDelay(Task task, long initialDelay, long delay, TimeUnit unit) {
		try {
			LOGGER.info("scheduling periodic " + task);
			onSubmit(task);
			periodicTasks.scheduleWithFixedDelay(new RunnableTask(task), initialDelay, delay, unit);
		}
		catch (RejectedExecutionException e) {
			LOGGER.warning(task + " rejected, probably because the node is shutting down");
		}
	}
}