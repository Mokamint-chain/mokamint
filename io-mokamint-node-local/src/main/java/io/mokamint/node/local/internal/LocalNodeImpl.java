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
import java.util.function.Predicate;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Stream;

import io.hotmoka.annotations.OnThread;
import io.hotmoka.annotations.ThreadSafe;
import io.mokamint.application.api.Application;
import io.mokamint.miner.api.Miner;
import io.mokamint.node.Chains;
import io.mokamint.node.NodeInfos;
import io.mokamint.node.Peers;
import io.mokamint.node.Versions;
import io.mokamint.node.api.Block;
import io.mokamint.node.api.Chain;
import io.mokamint.node.api.ChainInfo;
import io.mokamint.node.api.ClosedNodeException;
import io.mokamint.node.api.DatabaseException;
import io.mokamint.node.api.PeerAdditionRejectedException;
import io.mokamint.node.api.NodeInfo;
import io.mokamint.node.api.Peer;
import io.mokamint.node.api.PeerInfo;
import io.mokamint.node.api.Version;
import io.mokamint.node.local.AlreadyInitializedException;
import io.mokamint.node.local.Config;
import io.mokamint.node.local.LocalNode;
import io.mokamint.node.local.internal.blockchain.Blockchain;
import io.mokamint.node.local.internal.blockchain.VerificationException;
import io.mokamint.node.messages.MessageMemories;
import io.mokamint.node.messages.MessageMemory;
import io.mokamint.node.messages.api.WhisperBlockMessage;
import io.mokamint.node.messages.api.WhisperPeersMessage;
import io.mokamint.node.messages.api.Whisperer;
import io.mokamint.node.service.api.PublicNodeService;

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
	 * The application running over this node.
	 */
	private final Application app;

	/**
	 * The miners connected to the node.
	 */
	private final NodeMiners miners;

	/**
	 * The peers of the node.
	 */
	private final NodePeers peers;

	/**
	 * The database containing the blockchain.
	 */
	private final Database db;

	/**
	 * The blockchain of this node.
	 */
	private final Blockchain blockchain;

	/**
	 * The version of this node.
	 */
	private final Version version;

	/**
	 * The UUID of this node.
	 */
	private final UUID uuid;

	/**
	 * The executor of events. There might be more events in execution at the same time.
	 */
	private final ExecutorService events = Executors.newCachedThreadPool();

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
	 * The lock used to block new calls when the node has been requested to close.
	 */
	private final ClosureLock closureLock = new ClosureLock();

	private final static Logger LOGGER = Logger.getLogger(LocalNodeImpl.class.getName());

	/**
	 * Creates a local node of a Mokamint blockchain, for the given application,
	 * using the given miners.
	 * 
	 * @param config the configuration of the node
	 * @param app the application
	 * @param init if true, creates a genesis block and starts mining on top
	 *             (initial synchronization is consequently skipped)
	 * @param miners the miners
	 * @throws NoSuchAlgorithmException if some block in the database uses an unknown hashing algorithm
	 * @throws IOException if the version information cannot be read
	 * @throws DatabaseException if the database is corrupted
	 * @throws InterruptedException if the initialization of the node was interrupted
	 * @throws AlreadyInitializedException if {@code init} is true but the database of the node
	 *                                     contains a genesis block already
	 */
	public LocalNodeImpl(Config config, Application app, boolean init, Miner... miners) throws NoSuchAlgorithmException, DatabaseException, IOException, InterruptedException, AlreadyInitializedException {
		try {
			this.config = config;
			this.app = app;
			this.db = new Database(this, init);
			this.version = Versions.current();
			this.uuid = db.getUUID();
			this.whisperedMessages = MessageMemories.of(config.whisperingMemorySize);
			this.miners = new NodeMiners(this, Stream.of(miners));
			this.blockchain = new Blockchain(this);
			this.peers = new NodePeers(this);
			peers.tryToAdd(config.seeds().map(Peers::of), true, true);

			if (init)
				blockchain.startMining();
			else
				blockchain.startSynchronization(0L);
		}
		catch (ClosedDatabaseException | ClosedNodeException e) {
			// the database and the node itself cannot be closed already
			LOGGER.log(Level.SEVERE, "unexpected exception", e);
			throw new RuntimeException("unexpected exception", e);
		}
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
	public void whisper(WhisperBlockMessage message, Predicate<Whisperer> seen) {
		if (seen.test(this) || !whisperedMessages.add(message))
			return;

		try {
			blockchain.add(message.getBlock());
		}
		catch (NoSuchAlgorithmException | DatabaseException | VerificationException | ClosedDatabaseException e) {
			LOGGER.log(Level.SEVERE, "the whispered block " +
				message.getBlock().getHexHash(config.getHashingForBlocks()) +
				" could not be added to the blockchain: " + e.getMessage());
		}

		Predicate<Whisperer> newSeen = seen.or(Predicate.isEqual(this));
		peers.whisper(message, newSeen);
		boundWhisperers.forEach(whisperer -> whisperer.whisper(message, newSeen));
	}

	@Override
	public Optional<Block> getBlock(byte[] hash) throws DatabaseException, NoSuchAlgorithmException, ClosedNodeException {
		closureLock.beforeCall(ClosedNodeException::new);

		try {
			return db.getBlock(hash);
		}
		catch (ClosedDatabaseException e) {
			// the database cannot be closed because this node is open
			LOGGER.log(Level.SEVERE, "unexpected exception", e);
			throw new RuntimeException("unexpected exception", e);
		}
		finally {
			closureLock.afterCall();
		}
	}

	@Override
	public Stream<PeerInfo> getPeerInfos() throws ClosedNodeException {
		closureLock.beforeCall(ClosedNodeException::new);

		try {
			return peers.get();
		}
		finally {
			closureLock.afterCall();
		}
	}

	@Override
	public void addPeer(Peer peer) throws TimeoutException, InterruptedException, ClosedNodeException, IOException, PeerAdditionRejectedException, DatabaseException {
		closureLock.beforeCall(ClosedNodeException::new);

		try {
			peers.add(peer, true, true);
		}
		catch (ClosedDatabaseException e) {
			// the database cannot be closed because this node is open
			LOGGER.log(Level.SEVERE, "unexpected exception", e);
			throw new RuntimeException("unexpected exception", e);
		}
		finally {
			closureLock.afterCall();
		}
	}

	@Override
	public void removePeer(Peer peer) throws DatabaseException, ClosedNodeException, InterruptedException, IOException {
		closureLock.beforeCall(ClosedNodeException::new);

		try {
			peers.remove(peer);
		}
		catch (ClosedDatabaseException e) {
			// the database cannot be closed because this node is open
			LOGGER.log(Level.SEVERE, "unexpected exception", e);
			throw new RuntimeException("unexpected exception", e);
		}
		finally {
			closureLock.afterCall();
		}
	}

	@Override
	public void close() throws InterruptedException, DatabaseException, IOException {
		if (closureLock.stopNewCalls()) {
			events.shutdownNow();
			tasks.shutdownNow();
			periodicTasks.shutdownNow();

			InterruptedException interruptedException = null;
			IOException ioException = null;
			
			for (var handler: onCloseHandlers) {
				try {
					handler.close();
				}
				catch (InterruptedException e) {
					interruptedException = e;
				}
				catch (IOException e) {
					ioException = e;
				}
			}

			try {
				events.awaitTermination(3, TimeUnit.SECONDS);
				tasks.awaitTermination(3, TimeUnit.SECONDS);
				periodicTasks.awaitTermination(3, TimeUnit.SECONDS);
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
			else if (ioException != null)
				throw ioException;
		}
	}

	@Override
	public NodeInfo getInfo() throws ClosedNodeException {
		closureLock.beforeCall(ClosedNodeException::new);

		try {
			return NodeInfos.of(version, uuid, LocalDateTime.now(ZoneId.of("UTC")));
		}
		finally {
			closureLock.afterCall();
		}
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
	public ChainInfo getChainInfo() throws DatabaseException, ClosedNodeException {
		closureLock.beforeCall(ClosedNodeException::new);

		try {
			return blockchain.getChainInfo();
		}
		catch (ClosedDatabaseException e) {
			// the database cannot be closed because this node is open
			LOGGER.log(Level.SEVERE, "unexpected exception", e);
			throw new RuntimeException("unexpected exception", e);
		}
		finally {
			closureLock.afterCall();
		}
	}

	@Override
	public Chain getChain(long start, long count) throws DatabaseException, ClosedNodeException {
		closureLock.beforeCall(ClosedNodeException::new);
		
		try {
			return Chains.of(blockchain.getChain(start, count));
		}
		catch (ClosedDatabaseException e) {
			// the database cannot be closed because this node is open
			LOGGER.log(Level.SEVERE, "unexpected exception", e);
			throw new RuntimeException("unexpected exception", e);
		}
		finally {
			closureLock.afterCall();
		}
	}

	/**
	 * Yields the application running over this node.
	 * 
	 * @return the application
	 */
	public Application getApplication() {
		return app;
	}

	/**
	 * Yields the database in this node.
	 * 
	 * @return the database
	 */
	public Database getDatabase() {
		return db;
	}

	/**
	 * Yields the peers of this node.
	 * 
	 * @return the peers
	 */
	public NodePeers getPeers() {
		return peers;
	}

	/**
	 * Yields the miners of this node.
	 * 
	 * @return the miners
	 */
	public NodeMiners getMiners() {
		return miners;
	}

	/**
	 * Yields the blockchain of this node.
	 * 
	 * @return the blockchain
	 */
	public Blockchain getBlockchain() {
		return blockchain;
	}

	/**
	 * Adverts to its peers the services published on this node.
	 */
	void whisperItsServices() {
		for (var whisperer: boundWhisperers)
			if (whisperer instanceof PublicNodeService service)
				service.whisperItself();
	}

	private void whisper(WhisperPeersMessage message, Predicate<Whisperer> seen, boolean tryToAddToThePeers) {
		if (seen.test(this) || !whisperedMessages.add(message))
			return;

		Predicate<Whisperer> newSeen = seen.or(Predicate.isEqual(this));
		peers.whisper(message, newSeen, tryToAddToThePeers);
		boundWhisperers.forEach(whisperer -> whisperer.whisper(message, newSeen));
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

		/**
		 * Yields a prefix to be reported in the logs in front of the {@link #toString()} message.
		 * 
		 * @return the prefix
		 */
		String logPrefix();

		@Override
		String toString();
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
	public void submit(Event event) {
		var runnable = new Runnable() {

			@Override @OnThread("events")
			public final void run() {
				onStart(event);

				try {
					event.body();
				}
				catch (InterruptedException e) {
					LOGGER.log(Level.WARNING, event.logPrefix() + event + " interrupted");
					Thread.currentThread().interrupt();
					return;
				}
				catch (Exception e) {
					onFail(event, e);
					return;
				}

				onComplete(event);
			}
		};

		try {
			LOGGER.info(event.logPrefix() + "received " + event);
			onSubmit(event);
			events.execute(runnable);
		}
		catch (RejectedExecutionException e) {
			LOGGER.warning(event.logPrefix() + event + " rejected, probably because the node is shutting down");
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

		/**
		 * Yields a prefix to be reported in the logs in front of the {@link #toString()} message.
		 * 
		 * @return the prefix
		 */
		String logPrefix();

		@Override
		String toString();
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
		LOGGER.log(Level.SEVERE, task.logPrefix() + "failed execution of " + task, e);
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
			catch (InterruptedException e) {
				LOGGER.log(Level.WARNING, task.logPrefix() + task + " interrupted");
				Thread.currentThread().interrupt();
				return;
			}
			catch (Exception e) {
				onFail(task, e);
				return;
			}

			onComplete(task);
		}
	};

	/**
	 * Runs the given task, asynchronously, in one thread from the {@link #tasks} executor.
	 * 
	 * @param task the task to run
	 */
	public void submit(Task task) {
		try {
			LOGGER.info(task.logPrefix() + "scheduling " + task);
			onSubmit(task);
			tasks.execute(new RunnableTask(task));
		}
		catch (RejectedExecutionException e) {
			LOGGER.warning(task.logPrefix() + task + " rejected, probably because the node is shutting down");
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
	public void submitWithFixedDelay(Task task, long initialDelay, long delay, TimeUnit unit) {
		try {
			LOGGER.info(task.logPrefix() + "scheduling periodic " + task);
			onSubmit(task);
			periodicTasks.scheduleWithFixedDelay(new RunnableTask(task), initialDelay, delay, unit);
		}
		catch (RejectedExecutionException e) {
			LOGGER.warning(task.logPrefix() + task + " rejected, probably because the node is shutting down");
		}
	}
}