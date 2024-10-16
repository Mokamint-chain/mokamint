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

import java.math.BigInteger;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.Optional;
import java.util.concurrent.TimeoutException;

import io.hotmoka.crypto.Hex;
import io.hotmoka.crypto.api.Hasher;
import io.mokamint.application.api.ApplicationException;
import io.mokamint.application.api.UnknownGroupIdException;
import io.mokamint.application.api.UnknownStateException;
import io.mokamint.node.api.Block;
import io.mokamint.node.api.GenesisBlock;
import io.mokamint.node.api.NodeException;
import io.mokamint.node.api.NonGenesisBlock;
import io.mokamint.node.api.Transaction;
import io.mokamint.node.api.TransactionRejectedException;
import io.mokamint.node.local.api.LocalNodeConfig;
import io.mokamint.nonce.api.Deadline;

/**
 * A verifier of the consensus rules of the blocks that gets added to a blockchain.
 */
public class BlockVerification {

	/**
	 * The node having the blockchain whose blocks get verified.
	 */
	private final LocalNodeImpl node;

	/**
	 * The Xodus transaction during which verification occurs.
	 */
	private final io.hotmoka.xodus.env.Transaction txn;

	/**
	 * The configuration of {@link #node}.
	 */
	private final LocalNodeConfig config;

	/**
	 * The hasher for the application transactions.
	 */
	private final Hasher<Transaction> hasherForTransactions;

	/**
	 * The block to verify.
	 */
	private final Block block;

	/**
	 * The previous of the block to verify. This is {@code null} if and only if
	 * {@link #block} is a genesis block.
	 */
	private final Block previous;

	/**
	 * The deadline of {@link #block}. This is {@code null} if and only if
	 * {@link #block} is a genesis block.
	 */
	private final Deadline deadline;

	/**
	 * The creation time of the block under verification.
	 */
	private final LocalDateTime creationTime;

	/**
	 * Performs the verification that {@code block} satisfies all consensus rules required
	 * for being a child of {@code previous}, in the given {@code node}.
	 * 
	 * @param txn the Xodus transaction during which verification occurs
	 * @param node the node whose blocks get verified
	 * @param block the block
	 * @param previous the previous of {@code block}, already in blockchain; this can be empty only if {@code block} is a genesis block
	 * @throws VerificationException if verification fails
	 * @throws NodeException if the node is misbehaving
	 * @throws InterruptedException if the current thread was interrupted while waiting for an answer from the application
	 * @throws TimeoutException if some operation timed out
	 */
	BlockVerification(io.hotmoka.xodus.env.Transaction txn, LocalNodeImpl node, Block block, Optional<Block> previous) throws VerificationException, NodeException, InterruptedException, TimeoutException {
		this.txn = txn;
		this.node = node;
		this.hasherForTransactions = node.getHasherForTransactions();
		this.config = node.getConfig();
		this.block = block;
		this.previous = previous.orElse(null);
		this.deadline = block instanceof NonGenesisBlock ngb ? ngb.getDeadline() : null;
		this.creationTime = node.getBlockchain().creationTimeOf(txn, block).orElseThrow(() -> new NodeException("Cannot determine the creation time of the block under verification"));

		if (block instanceof NonGenesisBlock ngb)
			verifyAsNonGenesis(ngb);
		else
			verifyAsGenesis((GenesisBlock) block);
	}

	/**
	 * Verifies if the genesis {@link #block} is valid for the blockchain in {@link #node}.
	 * 
	 * @param block the same as the field {@link #block}, but cast into its actual type
	 * @throws VerificationException if verification fails
	 * @throws InterruptedException if the current thread was interrupted while waiting for an answer from the application
	 * @throws TimeoutException if some operation timed out
	 * @throws NodeException if the node is misbehaving
	 */
	private void verifyAsGenesis(GenesisBlock block) throws VerificationException, NodeException, InterruptedException, TimeoutException {
		creationTimeIsNotTooMuchInTheFuture();
		blockMatchesItsExpectedDescription(block);
		finalStateIsTheInitialStateOfTheApplication();
	}

	/**
	 * Verifies if the non-genesis {@link #block} satisfies all consensus rules required for being
	 * a child of {@link #previous}. This method is called only if the database is not empty.
	 * Namely, if {@link #previous} is in the database and if the genesis block of the database is set.
	 * It is guaranteed that the previous hash inside {@link #block} coincides with the hash
	 * of {@link #previous} (hence this condition is not verified by this method).
	 * 
	 * @param block the same as the field {@link #block}, but cast into its actual type
	 * @throws VerificationException if verification fails
	 * @throws NodeException if the node is misbehaving
	 * @throws InterruptedException if the current thread was interrupted while waiting for an answer from the application
	 * @throws TimeoutException if some operation timed out
	 * @throws NodeException if the node is misbehaving
	 */
	private void verifyAsNonGenesis(NonGenesisBlock block) throws VerificationException, NodeException, InterruptedException, TimeoutException {
		creationTimeIsNotTooMuchInTheFuture();
		deadlineMatchesItsExpectedChallenge();
		deadlineHasValidProlog();
		deadlineIsValid();
		blockMatchesItsExpectedDescription(block);
		transactionsSizeIsNotTooBig(block);
		transactionsAreNotAlreadyInBlockchain(block);
		transactionsExecutionLeadsToFinalState(block);
	}

	/**
	 * Checks if the deadline of {@link #block} is valid.
	 * 
	 * @throws VerificationException if that condition in violated
	 */
	private void deadlineIsValid() throws VerificationException {
		if (!deadline.isValid())
			throw new VerificationException("Invalid deadline");
	}

	/**
	 * Checks if the deadline of {@link #block} has a prolog valid for the {@link #node}.
	 * 
	 * @throws VerificationException if that condition in violated
	 * @throws NodeException if the node is misbehaving
	 * @throws InterruptedException if the current thread was interrupted while waiting for an answer from the application
	 * @throws TimeoutException if some operation timed out
	 */
	private void deadlineHasValidProlog() throws VerificationException, NodeException, InterruptedException, TimeoutException {
		var prolog = deadline.getProlog();

		if (!prolog.getChainId().equals(config.getChainId()))
			throw new VerificationException("Deadline prolog's chainId mismatch");

		if (!prolog.getSignatureForBlocks().equals(config.getSignatureForBlocks()))
			throw new VerificationException("Deadline prolog's signature algorithm for blocks mismatch");

		if (!prolog.getSignatureForDeadlines().equals(config.getSignatureForDeadlines()))
			throw new VerificationException("Deadline prolog's signature algorithm for deadlines mismatch");

		try {
			if (!node.getApplication().checkPrologExtra(prolog.getExtra()))
				throw new VerificationException("Invalid deadline prolog's extra");
		}
		catch (ApplicationException e) {
			throw new NodeException(e);
		}
	}

	/**
	 * Checks if the deadline of {@link #block} matches its expected challenge.
	 * 
	 * @throws VerificationException if that condition in violated
	 */
	private void deadlineMatchesItsExpectedChallenge() throws VerificationException {
		var challenge = previous.getNextChallenge(config.getHashingForGenerations(), config.getHashingForDeadlines());
		deadline.getChallenge().matchesOrThrow(challenge, message -> new VerificationException("Deadline mismatch: " + toLowerInitial(message)));
	}

	private static String toLowerInitial(String message) {
		if (message.isEmpty())
			return message;
		else
			return Character.toLowerCase(message.charAt(0)) + message.substring(1);
	}

	/**
	 * Checks if {@link #block} matches its expected description.
	 * 
	 * @param block the same as the field {@link #block}, but cast into its actual type
	 * @throws VerificationException if that condition in violated
	 */
	private void blockMatchesItsExpectedDescription(GenesisBlock block) throws VerificationException {
		var description = block.getDescription();

		var acceleration = description.getAcceleration();
		// TODO
		var generationSignature = new byte[config.getHashingForGenerations().length()];
		generationSignature[0] = (byte) 0x80;
		//var expectedAcceleration = new BigInteger(1, generationSignature).divide(BigInteger.valueOf(config.getTargetBlockCreationTime()));
		var expectedAcceleration = BigInteger.valueOf(config.getInitialAcceleration());

		if (!acceleration.equals(expectedAcceleration))
			throw new VerificationException("Acceleration mismatch (expected " + expectedAcceleration + " but found " + acceleration + ")");

		var signatureForBlocks = description.getSignatureForBlock();
		var expectedSignatureForBlocks = config.getSignatureForBlocks();
		if (!signatureForBlocks.equals(expectedSignatureForBlocks))
			throw new VerificationException("Block signature algorithm mismatch (expected " + expectedSignatureForBlocks + " but found " + signatureForBlocks + ")");
	}

	/**
	 * Checks if {@link #block} matches its expected description.
	 * 
	 * @param block the same as the field {@link #block}, but cast into its actual type
	 * @throws VerificationException if that condition in violated
	 */
	private void blockMatchesItsExpectedDescription(NonGenesisBlock block) throws VerificationException {
		var expectedDescription = previous.getNextBlockDescription(deadline, config);

		var description = block.getDescription();
		var height = description.getHeight();
		if (height != expectedDescription.getHeight())
			throw new VerificationException("Height mismatch (expected " + expectedDescription.getHeight() + " but found " + height + ")");

		var acceleration = description.getAcceleration();
		if (!acceleration.equals(expectedDescription.getAcceleration()))
			throw new VerificationException("Acceleration mismatch (expected " + expectedDescription.getAcceleration() + " but found " + acceleration + ")");

		var power = description.getPower();
		if (!power.equals(expectedDescription.getPower()))
			throw new VerificationException("Power mismatch (expected " + expectedDescription.getPower() + " but found " + power + ")");

		var totalWaitingTime = description.getTotalWaitingTime();
		if (totalWaitingTime != expectedDescription.getTotalWaitingTime())
			throw new VerificationException("Total waiting time mismatch (expected " + expectedDescription.getTotalWaitingTime() + " but found " + totalWaitingTime + ")");

		var weightedWaitingTime = description.getWeightedWaitingTime();
		if (weightedWaitingTime != expectedDescription.getWeightedWaitingTime())
			throw new VerificationException("Weighted waiting time mismatch (expected " + expectedDescription.getWeightedWaitingTime() + " but found " + weightedWaitingTime + ")");

		var hashOfPreviousBlock = description.getHashOfPreviousBlock();
		if (!Arrays.equals(hashOfPreviousBlock, expectedDescription.getHashOfPreviousBlock()))
			throw new VerificationException("Hash of previous block mismatch");
	}

	/**
	 * Checks if the creation time of {@link #block} is not too much in the future.
	 * 
	 * @throws VerificationException if the creationTime of {@link #block} is too much in the future
	 * @throws NodeException if the node is misbehaving
	 */
	private void creationTimeIsNotTooMuchInTheFuture() throws VerificationException, NodeException {
		LocalDateTime now = node.getPeers().asNetworkDateTime(LocalDateTime.now(ZoneId.of("UTC")));
		long howMuchInTheFuture = ChronoUnit.MILLIS.between(now, creationTime);
		long max = node.getConfig().getBlockMaxTimeInTheFuture();
		if (howMuchInTheFuture > max)
			throw new VerificationException("Too much in the future (" + howMuchInTheFuture + " ms against an allowed maximum of " + max + " ms)");
	}

	private void transactionsSizeIsNotTooBig(NonGenesisBlock block) throws VerificationException {
		if (block.getTransactions().mapToLong(Transaction::size).sum() > config.getMaxBlockSize())
			throw new VerificationException("The table of transactions is too big (maximum is " + config.getMaxBlockSize() + ")");
	}

	/**
	 * Checks that the transactions in {@link #block} are not already contained in blockchain.
	 * 
	 * @param block the same as the field {@link #block}, but cast into its actual type
	 * @throws VerificationException if some transaction is already contained in blockchain
	 * @throws NodeException if the node is misbehaving
	 */
	private void transactionsAreNotAlreadyInBlockchain(NonGenesisBlock block) throws VerificationException, NodeException {
		for (var tx: block.getTransactions().toArray(Transaction[]::new)) {
			var txHash = hasherForTransactions.hash(tx);
			if (node.getBlockchain().getTransactionAddress(txn, previous, txHash).isPresent())
				throw new VerificationException("Repeated transaction " + Hex.toHexString(txHash));
		}
	}

	/**
	 * Checks that the execution of the transactions inside {@link #block} is successful
	 * (both check and delivery of transactions succeeds) and leads to the final state
	 * whose hash is in the {@link #block}.
	 * 
	 * @param block the same as the field {@link #block}, but cast into its actual type
	 * @throws VerificationException if that condition does not hold
	 * @throws InterruptedException if the current thread was interrupted while waiting for an answer from the application
	 * @throws TimeoutException if some operation timed out
	 * @throws NodeException if the node is misbehaving
	 */
	private void transactionsExecutionLeadsToFinalState(NonGenesisBlock block) throws VerificationException, InterruptedException, TimeoutException, NodeException {
		var app = node.getApplication();

		var creationTimeOfPrevious = node.getBlockchain().creationTimeOf(txn, previous);
		if (creationTimeOfPrevious.isEmpty())
			throw new NodeException("The previous of the block under verification was expected to be in blockchain");

		try {
			int id;

			try {
				id = app.beginBlock(block.getDescription().getHeight(), creationTimeOfPrevious.get(), previous.getStateId());
			}
			catch (UnknownStateException e) {
				throw new VerificationException("Block verification failed because its initial state is unknown to the application: " + e.getMessage());
			}

			boolean success = false;

			try {
				for (var tx: block.getTransactions().toArray(Transaction[]::new)) {
					try {
						app.checkTransaction(tx);
					}
					catch (TransactionRejectedException e) {
						throw new VerificationException("Failed check of transaction " + tx.getHexHash(hasherForTransactions) + ": " + e.getMessage());
					}

					try {
						app.deliverTransaction(id, tx);
					}
					catch (TransactionRejectedException e) {
						throw new VerificationException("Failed delivery of transaction " + tx.getHexHash(hasherForTransactions) + ": " + e.getMessage());
					}
				}

				var found = app.endBlock(id, block.getDeadline());

				if (!Arrays.equals(block.getStateId(), found))
					throw new VerificationException("Final state mismatch (expected " + Hex.toHexString(block.getStateId()) + " but found " + Hex.toHexString(found) + ")");

				success = true;
			}
			finally {
				if (success)
					app.commitBlock(id);
				else
					app.abortBlock(id);
			}
		}
		catch (UnknownGroupIdException e) {
			// somebody has closed the group id that we are using: the node is not working properly
			throw new NodeException(e);
		}
		catch (ApplicationException e) {
			// the node is misbehaving because the application it is connected to is misbehaving
			throw new NodeException(e);
		}
	}

	private void finalStateIsTheInitialStateOfTheApplication() throws VerificationException, InterruptedException, TimeoutException, NodeException {
		try {
			var expected = node.getApplication().getInitialStateId();
			if (!Arrays.equals(block.getStateId(), expected))
				throw new VerificationException("Final state mismatch (expected " + Hex.toHexString(expected) + " but found " + Hex.toHexString(block.getStateId()) + ")");
		}
		catch (ApplicationException e) {
			// the node is misbehaving because the application it is connected to is misbehaving
			throw new NodeException(e);
		}
	}
}