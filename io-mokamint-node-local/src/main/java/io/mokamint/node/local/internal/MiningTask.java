package io.mokamint.node.local.internal;

import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.SignatureException;
import java.util.Optional;
import java.util.concurrent.RejectedExecutionException;
import java.util.logging.Level;
import java.util.logging.Logger;

import io.mokamint.node.api.Block;
import io.mokamint.node.api.DatabaseException;
import io.mokamint.node.local.internal.LocalNodeImpl.Task;
import io.mokamint.node.local.internal.Mempool.TransactionEntry;

public class MiningTask implements Task {
	private final MiningThread miningThread = new MiningThread();
	private volatile BlockMiner blockMiner;
	private volatile boolean taskHasBeenInterrupted;
	private final LocalNodeImpl node;
	private final static Logger LOGGER = Logger.getLogger(MiningTask.class.getName());

	public MiningTask(LocalNodeImpl node) {
		this.node = node;
	}

	@Override
	public void body() {
		miningThread.start();

		try {
			miningThread.join();
		}
		catch (InterruptedException e) {
			taskHasBeenInterrupted = true;
			miningThread.interrupt();
		}
	}

	public void restartFromCurrentHead() {
		miningThread.interrupt(); // this will interrupt the current mining activity and restart it on top of the new head
	}

	public void add(TransactionEntry entry) throws NoSuchAlgorithmException, ClosedDatabaseException, DatabaseException {
		var blockMiner = this.blockMiner;
		if (blockMiner != null)
			blockMiner.add(entry);
	}

	private class MiningThread extends Thread {

		@Override
		public void run() {
			while (!taskHasBeenInterrupted) {
				try {
					Optional<Block> maybeHead = node.getBlockchain().getHead();

					if (maybeHead.isEmpty()) {
						LOGGER.warning("mining: cannot mine on an empty blockchain, will retry later");
						Thread.sleep(2000);
					}
					else if (node.getMiners().get().count() == 0L) {
						LOGGER.warning("mining: cannot mine with no miners attached, will retry later");
						node.onNoMinersAvailable();
						Thread.sleep(2000);
					}
					else if (node.isSynchronizing()) {
						LOGGER.warning("mining: delaying mining since synchronization is in progress, will retry later");
						Thread.sleep(2000);
					}
					else {
						var head = maybeHead.get();
						LOGGER.info("mining: starting mining over block " + head.getHexHash(node.getConfig().getHashingForBlocks()));
						blockMiner = new BlockMiner(node, head);
						blockMiner.run();
					}
				}
				catch (InterruptedException e) {
					LOGGER.info("mining: restarting mining since the blockchain's head changed");
				}
				catch (ClosedDatabaseException e) {
					LOGGER.warning("mining: exiting since the database has been closed");
					break;
				}
				catch (RejectedExecutionException e) {
					LOGGER.warning("mining: exiting since the node is being shut down");
					break;
				}
				catch (NoSuchAlgorithmException | DatabaseException | InvalidKeyException | SignatureException e) {
					LOGGER.log(Level.SEVERE, "mining: exiting because of exception", e);
					break;
				}
				catch (RuntimeException e) {
					LOGGER.log(Level.SEVERE, "mining: exiting because of unexpected exception", e);
					break;
				}
			}
		}
	}
}