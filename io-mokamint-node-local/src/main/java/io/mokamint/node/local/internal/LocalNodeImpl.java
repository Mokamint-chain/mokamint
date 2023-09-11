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
import java.security.KeyPair;
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
import io.mokamint.miner.remote.RemoteMiners;
import io.mokamint.node.Chains;
import io.mokamint.node.NodeInfos;
import io.mokamint.node.Versions;
import io.mokamint.node.api.Block;
import io.mokamint.node.api.Chain;
import io.mokamint.node.api.ChainInfo;
import io.mokamint.node.api.ClosedNodeException;
import io.mokamint.node.api.DatabaseException;
import io.mokamint.node.api.MinerInfo;
import io.mokamint.node.api.NodeInfo;
import io.mokamint.node.api.Peer;
import io.mokamint.node.api.PeerInfo;
import io.mokamint.node.api.PeerRejectedException;
import io.mokamint.node.api.Version;
import io.mokamint.node.local.AlreadyInitializedException;
import io.mokamint.node.local.Config;
import io.mokamint.node.local.LocalNode;
import io.mokamint.node.local.internal.blockchain.Blockchain;
import io.mokamint.node.local.internal.blockchain.VerificationException;
import io.mokamint.node.messages.MessageMemories;
import io.mokamint.node.messages.api.MessageMemory;
import io.mokamint.node.messages.api.WhisperBlockMessage;
import io.mokamint.node.messages.api.WhisperPeersMessage;
import io.mokamint.node.messages.api.Whisperer;
import io.mokamint.node.service.api.PublicNodeService;
import io.mokamint.nonce.api.Deadline;
import jakarta.websocket.DeploymentException;

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
	 * The key pair that the node uses to sign the blocks that it mines.
	 */
	private final KeyPair keyPair;

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
	 * The executor of tasks and events. There might be more tasks and events in execution at the same time.
	 */
	private final ExecutorService executors = Executors.newCachedThreadPool();

	/**
	 * The executor of periodic tasks. There might be more periodic tasks in execution
	 * at the same time.
	 */
	private final ScheduledExecutorService periodExecutors = Executors.newScheduledThreadPool(5);

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
	 * @param keyPair the key pair that the node will use to sign the blocks that it mines
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
	public LocalNodeImpl(Config config, KeyPair keyPair, Application app, boolean init, Miner... miners) throws NoSuchAlgorithmException, DatabaseException, IOException, InterruptedException, AlreadyInitializedException {
		try {
			this.config = config;
			this.keyPair = keyPair;
			this.app = app;
			this.version = Versions.current();
			this.whisperedMessages = MessageMemories.of(config.whisperingMemorySize);
			this.miners = new NodeMiners(this, Stream.of(miners));
			this.blockchain = new Blockchain(this, init);
			this.peers = new NodePeers(this);
			this.uuid = peers.getUUID();
			peers.connect();

			if (init)
				blockchain.startMining();
			else
				blockchain.startSynchronization(0L);
		}
		catch (ClosedDatabaseException | ClosedNodeException e) {
			// the database and the node itself cannot be closed already
			LOGGER.log(Level.SEVERE, "unexpected exception", e);
			throw new RuntimeException("Unexpected exception", e);
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
			return blockchain.getBlock(hash);
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
	public Stream<MinerInfo> getMinerInfos() throws ClosedNodeException {
		closureLock.beforeCall(ClosedNodeException::new);

		try {
			return miners.getInfos();
		}
		finally {
			closureLock.afterCall();
		}
	}

	@Override
	public boolean add(Peer peer) throws TimeoutException, InterruptedException, ClosedNodeException, IOException, PeerRejectedException, DatabaseException {
		closureLock.beforeCall(ClosedNodeException::new);

		try {
			return peers.add(peer, true, true);
		}
		catch (ClosedDatabaseException e) {
			// the database cannot be closed because this node is open
			LOGGER.log(Level.SEVERE, "unexpected exception", e);
			throw new RuntimeException("Unexpected exception", e);
		}
		finally {
			closureLock.afterCall();
		}
	}

	@Override
	public boolean remove(Peer peer) throws DatabaseException, ClosedNodeException, InterruptedException, IOException {
		closureLock.beforeCall(ClosedNodeException::new);

		try {
			return peers.remove(peer);
		}
		catch (ClosedDatabaseException e) {
			// the database cannot be closed because this node is open
			LOGGER.log(Level.SEVERE, "unexpected exception", e);
			throw new RuntimeException("Unexpected exception", e);
		}
		finally {
			closureLock.afterCall();
		}
	}

	@Override
	public boolean openMiner(int port) throws IOException, ClosedNodeException {
		closureLock.beforeCall(ClosedNodeException::new);

		try {
			return miners.add(RemoteMiners.of(port, config.chainId, keyPair.getPublic()));
		}
		catch (DeploymentException e) {
			throw new IOException(e);
		}
		finally {
			closureLock.afterCall();
		}
	}

	@Override
	public boolean closeMiner(UUID uuid) throws ClosedNodeException {
		closureLock.beforeCall(ClosedNodeException::new);

		try {
			Optional<Miner> maybeMiner = miners.get().filter(miner -> miner.getUUID().equals(uuid)).findFirst();
			if (maybeMiner.isPresent()) {
				var miner = maybeMiner.get();
				if (miners.remove(miner)) {
					miner.close();
					return true;
				}
			}

			return false;
		}
		finally {
			closureLock.afterCall();
		}
	}

	@Override
	public void close() throws InterruptedException, DatabaseException, IOException {
		// TODO: you should close the miners
		if (closureLock.stopNewCalls()) {
			executors.shutdownNow();
			periodExecutors.shutdownNow();

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
				executors.awaitTermination(3, TimeUnit.SECONDS);
				periodExecutors.awaitTermination(3, TimeUnit.SECONDS);
			}
			finally {
				try {
					blockchain.close();
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
	 * Determines if a deadline is legal for this node. This means that:
	 * <ul>
	 * <li> it is valid
	 * <li> its prolog specifies the same chain identifier as the node
	 * <li> its prolog uses a node's public key that coincides with that of the node
	 * <li> the extra bytes of the prolog are valid for the application
	 * </ul>
	 * 
	 * @param deadline the deadline to check
	 * @return true if and only if all the above conditions hold
	 */
	public boolean isLegal(Deadline deadline) {
		var prolog = deadline.getProlog();

		return deadline.isValid() &&
			prolog.getChainId().equals(config.chainId) &&
			prolog.getNodePublicKey().equals(keyPair.getPublic()) &&
			app.prologExtraIsValid(prolog.getExtra());
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
		@OnThread("executors")
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
	 * Signals that an event occurred. This is typically called
	 * to signal that something occurred and that the node must react accordingly.
	 * 
	 * @param event the submitted event
	 */
	public void submit(Event event) {
		var runnable = new Runnable() {

			@Override @OnThread("executors")
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
			executors.execute(runnable);
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
		@OnThread("executors")
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

		@Override @OnThread("executors")
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
	 * Runs the given task, asynchronously, in one thread from the {@link #executors} executor.
	 * 
	 * @param task the task to run
	 */
	public void submit(Task task) {
		try {
			LOGGER.info(task.logPrefix() + "scheduling " + task);
			onSubmit(task);
			executors.execute(new RunnableTask(task));
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
	 * Runs the given task, periodically, with the {@link #periodExecutors} executor.
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
			periodExecutors.scheduleWithFixedDelay(new RunnableTask(task), initialDelay, delay, unit);
		}
		catch (RejectedExecutionException e) {
			LOGGER.warning(task.logPrefix() + task + " rejected, probably because the node is shutting down");
		}
	}
}