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
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.NoSuchElementException;
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
import io.mokamint.node.local.ApplicationTimeoutException;
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
	 * Verifies that {@code block} satisfies all consensus rules required
	 * for being a child of {@code previous}, in the given {@code node}.
	 * 
	 * @param txn the Xodus transaction during which verification occurs
	 * @param node the node whose blocks get verified
	 * @param block the block
	 * @param previous the previous of {@code block}, already in blockchain; this can be empty only if {@code block} is a genesis block
	 * @throws VerificationException if verification fails
	 * @throws NodeException if the node is misbehaving
	 * @throws InterruptedException if the current thread was interrupted while waiting for an answer from the application
	 * @throws ApplicationTimeoutException if the application of the Mokamint node is unresponsive
	 */
	BlockVerification(io.hotmoka.xodus.env.Transaction txn, LocalNodeImpl node, Block block, Optional<Block> previous) throws VerificationException, NodeException, InterruptedException, ApplicationTimeoutException {
		this.txn = txn;
		this.node = node;
		this.config = node.getConfig();
		this.hasherForTransactions = config.getHashingForTransactions().getHasher(io.mokamint.node.api.Transaction::toByteArray);
		this.block = block;
		this.previous = previous.orElse(null);
		this.deadline = block instanceof NonGenesisBlock ngb ? ngb.getDescription().getDeadline() : null;
		// the following exception should never happen, since the blockchain is non-empty for non-genesis blocks
		this.creationTime = node.getBlockchain().creationTimeOf(txn, block).orElseThrow(() -> new NoSuchElementException("Cannot determine the creation time of the block under verification"));

		if (block instanceof NonGenesisBlock ngb)
			verifyAsNonGenesis(ngb);
		else
			verifyAsGenesis((GenesisBlock) block);
	}

	/**
	 * Verifies if the genesis {@link #block} is valid.
	 * 
	 * @param block the same as the field {@link #block}, but cast into its actual type
	 * @throws VerificationException if verification fails
	 * @throws InterruptedException if the current thread gets interrupted
	 * @throws TimeoutException if some operation timed out
	 * @throws NodeException if the node is misbehaving
	 */
	private void verifyAsGenesis(GenesisBlock block) throws VerificationException, NodeException, InterruptedException, ApplicationTimeoutException {
		creationTimeIsNotTooMuchInTheFuture();
		blockMatchesItsExpectedDescription(block);
		finalStateIsTheInitialStateOfTheApplication();
	}

	/**
	 * Verifies if the non-genesis {@link #block} satisfies all consensus rules required for being
	 * a child of {@link #previous}. This method is called only if the blockchain is not empty.
	 * Namely, if {@link #previous} is in blockchain and if the genesis block of the blockchain is set.
	 * It is guaranteed that the previous hash reference in {@link #block} coincides with the hash
	 * of {@link #previous} (hence this condition is not verified by this method).
	 * 
	 * @param block the same as the field {@link #block}, but cast into its actual type
	 * @throws VerificationException if verification fails
	 * @throws NodeException if the node is misbehaving
	 * @throws InterruptedException if the current thread gets interrupted
	 * @throws ApplicationTimeoutException if the application of the Mokamint node is unresponsive
	 */
	private void verifyAsNonGenesis(NonGenesisBlock block) throws VerificationException, NodeException, InterruptedException, ApplicationTimeoutException {
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
	 * @throws InterruptedException if the current thread gets interrupted
	 * @throws ApplicationTimeoutException if the application of the Mokamint node is unresponsive
	 */
	private void deadlineHasValidProlog() throws VerificationException, NodeException, InterruptedException, ApplicationTimeoutException {
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
		catch (TimeoutException e) {
			throw new ApplicationTimeoutException(e);
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
		var challenge = previous.getDescription().getNextChallenge();
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

		var targetBlockCreationTime = description.getTargetBlockCreationTime();
		var expectedTargetBlockCreationTime = config.getTargetBlockCreationTime();
		if (targetBlockCreationTime != expectedTargetBlockCreationTime)
			throw new VerificationException("Target block creation time mismatch (expected " + expectedTargetBlockCreationTime + " but found " + targetBlockCreationTime + ")");

		var oblivion = description.getOblivion();
		var expectedOblivion = config.getOblivion();
		if (oblivion != expectedOblivion)
			throw new VerificationException("Oblivion mismatch (expected " + expectedOblivion + " but found " + oblivion + ")");

		var signatureForBlocks = description.getSignatureForBlocks();
		var expectedSignatureForBlocks = config.getSignatureForBlocks();
		if (!signatureForBlocks.equals(expectedSignatureForBlocks))
			throw new VerificationException("Block signature algorithm mismatch (expected " + expectedSignatureForBlocks + " but found " + signatureForBlocks + ")");

		var hashingForDeadlines = description.getHashingForDeadlines();
		var expectedHashingForDeadlines = config.getHashingForDeadlines();
		if (!hashingForDeadlines.equals(expectedHashingForDeadlines))
			throw new VerificationException("Deadline hashing algorithm mismatch (expected " + expectedHashingForDeadlines + " but found " + hashingForDeadlines + ")");

		var hashingForGenerations = description.getHashingForGenerations();
		var expectedHashingForGenerations = config.getHashingForGenerations();
		if (!hashingForGenerations.equals(expectedHashingForGenerations))
			throw new VerificationException("Generation hashing algorithm mismatch (expected " + expectedHashingForGenerations + " but found " + hashingForGenerations + ")");
	}

	/**
	 * Checks if {@link #block} matches its expected description.
	 * 
	 * @param block the same as the field {@link #block}, but cast into its actual type
	 * @throws VerificationException if that condition in violated
	 */
	private void blockMatchesItsExpectedDescription(NonGenesisBlock block) throws VerificationException {
		var expectedDescription = previous.getNextBlockDescription(deadline);
		var description = block.getDescription();

		long height = description.getHeight();
		long expectedHeight = expectedDescription.getHeight();
		if (height != expectedHeight)
			throw new VerificationException("Height mismatch (expected " + expectedHeight + " but found " + height + ")");

		var acceleration = description.getAcceleration();
		var expectedAcceleration = expectedDescription.getAcceleration();
		if (!acceleration.equals(expectedAcceleration))
			throw new VerificationException("Acceleration mismatch (expected " + expectedAcceleration + " but found " + acceleration + ")");

		var power = description.getPower();
		var expectedPower = expectedDescription.getPower();
		if (!power.equals(expectedPower))
			throw new VerificationException("Power mismatch (expected " + expectedPower + " but found " + power + ")");

		long totalWaitingTime = description.getTotalWaitingTime();
		long expectedTotalWaitingTime = expectedDescription.getTotalWaitingTime();
		if (totalWaitingTime != expectedTotalWaitingTime)
			throw new VerificationException("Total waiting time mismatch (expected " + expectedTotalWaitingTime + " but found " + totalWaitingTime + ")");

		long weightedWaitingTime = description.getWeightedWaitingTime();
		if (weightedWaitingTime != expectedDescription.getWeightedWaitingTime())
			throw new VerificationException("Weighted waiting time mismatch (expected " + expectedDescription.getWeightedWaitingTime() + " but found " + weightedWaitingTime + ")");

		byte[] hashOfPreviousBlock = description.getHashOfPreviousBlock();
		byte[] expectedHashOfPreviousBlock = expectedDescription.getHashOfPreviousBlock();
		if (!Arrays.equals(hashOfPreviousBlock, expectedHashOfPreviousBlock))
			throw new VerificationException("Hash of previous block mismatch");
	}

	/**
	 * Checks if the creation time of {@link #block} is not too much in the future.
	 * 
	 * @throws VerificationException if that condition is violated
	 */
	private void creationTimeIsNotTooMuchInTheFuture() throws VerificationException {
		LocalDateTime now = node.getPeers().asNetworkDateTime(LocalDateTime.now(ZoneId.of("UTC")));
		long howMuchInTheFuture = ChronoUnit.MILLIS.between(now, creationTime);
		long max = config.getBlockMaxTimeInTheFuture();
		if (howMuchInTheFuture > max)
			throw new VerificationException("Too much in the future (" + howMuchInTheFuture + " ms against an allowed maximum of " + max + " ms)");
	}

	private void transactionsSizeIsNotTooBig(NonGenesisBlock block) throws VerificationException {
		if (block.getTransactions().mapToLong(Transaction::size).sum() > config.getMaxBlockSize())
			throw new VerificationException("The table of transactions is too big (maximum is " + config.getMaxBlockSize() + " bytes)");
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
	 * (both check and delivery of transactions succeed) and leads to the final state
	 * whose hash is in the {@link #block}.
	 * 
	 * @param block the same as the field {@link #block}, but cast into its actual type
	 * @throws VerificationException if that condition is violated
	 * @throws InterruptedException if the current thread gets interrupted
	 * @throws ApplicationTimeoutException if the application of the Mokamint node is unresponsive
	 * @throws NodeException if the node is misbehaving
	 */
	private void transactionsExecutionLeadsToFinalState(NonGenesisBlock block) throws VerificationException, InterruptedException, ApplicationTimeoutException, NodeException {
		var app = node.getApplication();

		// if the following exception occurs, there is a coding error
		var creationTimeOfPrevious = node.getBlockchain().creationTimeOf(txn, previous)
			.orElseThrow(() -> new NoSuchElementException("The previous of the block under verification was expected to be in blockchain"));

		try {
			int id;

			try {
				id = app.beginBlock(block.getDescription().getHeight(), creationTimeOfPrevious, previous.getStateId());
			}
			catch (UnknownStateException e) {
				throw new VerificationException("The initial state is unknown to the application: " + e.getMessage());
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

				var found = app.endBlock(id, block.getDescription().getDeadline());

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
		catch (TimeoutException e) {
			throw new ApplicationTimeoutException(e);
		}
		catch (ApplicationException | UnknownGroupIdException e) {
			// the node is misbehaving because the application it is connected to is misbehaving
			throw new NodeException(e);
		}
	}

	private void finalStateIsTheInitialStateOfTheApplication() throws VerificationException, InterruptedException, ApplicationTimeoutException, NodeException {
		try {
			var expected = node.getApplication().getInitialStateId();
			if (!Arrays.equals(block.getStateId(), expected))
				throw new VerificationException("Final state mismatch (expected " + Hex.toHexString(expected) + " but found " + Hex.toHexString(block.getStateId()) + ")");
		}
		catch (ApplicationException e) {
			// the node is misbehaving because the application it is connected to is misbehaving
			throw new NodeException(e);
		}
		catch (TimeoutException e) {
			throw new ApplicationTimeoutException(e);
		}
	}
}