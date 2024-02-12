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
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.Optional;
import java.util.concurrent.TimeoutException;

import io.hotmoka.crypto.Hex;
import io.mokamint.application.api.UnknownStateException;
import io.mokamint.node.BlockDescriptions;
import io.mokamint.node.api.Block;
import io.mokamint.node.api.BlockDescription;
import io.mokamint.node.api.DatabaseException;
import io.mokamint.node.api.GenesisBlock;
import io.mokamint.node.api.NonGenesisBlock;
import io.mokamint.node.api.RejectedTransactionException;
import io.mokamint.node.api.Transaction;
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
	 * The configuration of {@link #node}.
	 */
	private final LocalNodeConfig config;

	/**
	 * The block to verify.
	 */
	private final Block block;

	/**
	 * The previous of the block to verify. This is {@code null} if and only if
	 * {@link #block} is a genesis block.
	 */
	private final Block previous;

	private final boolean commit;

	/**
	 * The deadline of {@link #block}. This is {@code null} if and only if
	 * {@link #block} is a genesis block.
	 */
	private final Deadline deadline;

	/**
	 * Performs the verification that {@code block} satisfies all consensus rules required
	 * for being a child of {@code previous}, in the given {@code node}.
	 * 
	 * @param node the node whose blocks get verified
	 * @param block the block
	 * @param previous the previous of {@code block}; this can be empty only if {@code block} is a genesis block
	 * @param commit if verification succeeds, commit the state at the end of the execution of the transactions in the block
	 * @throws VerificationException if verification fails
	 * @throws ClosedDatabaseException if the database is already closed
	 * @throws DatabaseException if the database is corrupted
	 * @throws NoSuchAlgorithmException if some block in blockchain refers to an unknown cryptographic algorithm
	 * @throws InterruptedException if the current thread was interrupted while waiting for an answer from the application
	 * @throws TimeoutException if the application did not provide an answer in time
	 */
	BlockVerification(LocalNodeImpl node, Block block, Optional<Block> previous, boolean commit) throws VerificationException, DatabaseException, ClosedDatabaseException, NoSuchAlgorithmException, TimeoutException, InterruptedException {
		this.node = node;
		this.config = node.getConfig();
		this.block = block;
		this.previous = previous.orElse(null);
		this.commit = commit;
		this.deadline = block instanceof NonGenesisBlock ngb ? ngb.getDeadline() : null;

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
	 * @throws ClosedDatabaseException if the database is already closed
	 * @throws DatabaseException if the database is corrupted
	 * @throws InterruptedException if the current thread was interrupted while waiting for an answer from the application
	 * @throws TimeoutException if the application did not provide an answer in time
	 */
	private void verifyAsGenesis(GenesisBlock block) throws VerificationException, DatabaseException, ClosedDatabaseException, TimeoutException, InterruptedException {
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
	 * @throws ClosedDatabaseException if the database is already closed
	 * @throws DatabaseException if the database is corrupted
	 * @throws NoSuchAlgorithmException if some block in blockchain refers to an unknown cryptographic algorithm
	 * @throws InterruptedException if the current thread was interrupted while waiting for an answer from the application
	 * @throws TimeoutException if the application did not provide an answer in time
	 */
	private void verifyAsNonGenesis(NonGenesisBlock block) throws VerificationException, DatabaseException, ClosedDatabaseException, NoSuchAlgorithmException, TimeoutException, InterruptedException {
		creationTimeIsNotTooMuchInTheFuture();
		deadlineMatchesItsExpectedDescription();
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
	 * @throws InterruptedException if the current thread was interrupted while waiting for an answer from the application
	 * @throws TimeoutException if the application did not provide an answer in time
	 */
	private void deadlineHasValidProlog() throws VerificationException, TimeoutException, InterruptedException {
		var prolog = deadline.getProlog();

		if (!prolog.getChainId().equals(config.getChainId()))
			throw new VerificationException("Deadline prolog's chainId mismatch");

		if (!prolog.getSignatureForBlocks().equals(config.getSignatureForBlocks()))
			throw new VerificationException("Deadline prolog's signature algorithm for blocks mismatch");

		if (!prolog.getSignatureForDeadlines().equals(config.getSignatureForDeadlines()))
			throw new VerificationException("Deadline prolog's signature algorithm for deadlines mismatch");

		if (!node.getApplication().checkPrologExtra(prolog.getExtra()))
			throw new VerificationException("Invalid deadline prolog's extra");
	}

	/**
	 * Checks if the deadline of {@link #block} matches its expected description.
	 * 
	 * @throws VerificationException if that condition in violated
	 */
	private void deadlineMatchesItsExpectedDescription() throws VerificationException {
		var description = previous.getNextDeadlineDescription(config.getHashingForGenerations(), config.getHashingForDeadlines());
		deadline.matchesOrThrow(description, message -> new VerificationException("Deadline mismatch: " + toLowerInitial(message)));
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
		BlockDescription description;

		try {
			description = BlockDescriptions.genesis(block.getStartDateTimeUTC(), BigInteger.valueOf(config.getInitialAcceleration()), config.getSignatureForBlocks(), block.getDescription().getPublicKeyForSigningBlock());
		}
		catch (InvalidKeyException e) {
			throw new VerificationException("The block contains an invalid key");
		}

		block.matchesOrThrow(description, VerificationException::new);
	}

	/**
	 * Checks if {@link #block} matches its expected description.
	 * 
	 * @param block the same as the field {@link #block}, but cast into its actual type
	 * @throws VerificationException if that condition in violated
	 */
	private void blockMatchesItsExpectedDescription(NonGenesisBlock block) throws VerificationException {
		var description = previous.getNextBlockDescription(deadline, config.getTargetBlockCreationTime(), config.getHashingForBlocks(), config.getHashingForDeadlines());
		block.matchesOrThrow(description, VerificationException::new);
	}

	/**
	 * Checks if the creation time of {@link #block} is not too much in the future.
	 * 
	 * @throws VerificationException if the creationTime of {@link #block} is too much in the future
	 * @throws ClosedDatabaseException if the database is already closed
	 * @throws DatabaseException if the database is corrupted
	 */
	private void creationTimeIsNotTooMuchInTheFuture() throws VerificationException, DatabaseException, ClosedDatabaseException {
		LocalDateTime now = node.getPeers().asNetworkDateTime(LocalDateTime.now(ZoneId.of("UTC")));
		long howMuchInTheFuture = ChronoUnit.MILLIS.between(now, node.getBlockchain().creationTimeOf(block));
		long max = node.getConfig().getBlockMaxTimeInTheFuture();
		if (howMuchInTheFuture > max)
			throw new VerificationException("Too much in the future (" + howMuchInTheFuture + " ms against an allowed maximum of " + max + " ms)");
	}

	private void transactionsSizeIsNotTooBig(NonGenesisBlock block) throws VerificationException {
		if (block.getTransactions().mapToInt(Transaction::size).sum() > config.getMaxBlockSize())
			throw new VerificationException("The table of transactions is too big (maximum is " + config.getMaxBlockSize() + ")");
	}

	/**
	 * Checks that the transactions in {@link #block} are not already contained in blockchain.
	 * 
	 * @param block the same as the field {@link #block}, but cast into its actual type
	 * @throws VerificationException if some transaction is already contained in blockchain
	 * @throws DatabaseException if the database is corrupted
	 * @throws ClosedDatabaseException if the database is already closed
	 * @throws NoSuchAlgorithmException if some block in blockchain refers to an unknown cryptographic algorithm
	 */
	private void transactionsAreNotAlreadyInBlockchain(NonGenesisBlock block) throws VerificationException, NoSuchAlgorithmException, ClosedDatabaseException, DatabaseException {
		for (var tx: block.getTransactions().toArray(Transaction[]::new)) {
			var txHash = node.getHasherForTransactions().hash(tx);
			if (node.getBlockchain().getTransactionAddress(previous, txHash).isPresent())
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
	 * @throws ClosedDatabaseException if the database is already closed
	 * @throws DatabaseException if the database is corrupted
	 * @throws InterruptedException if the current thread was interrupted while waiting for an answer from the application
	 * @throws TimeoutException if the application did not provide an answer in time
	 */
	private void transactionsExecutionLeadsToFinalState(NonGenesisBlock block) throws VerificationException, DatabaseException, ClosedDatabaseException, TimeoutException, InterruptedException {
		var app = node.getApplication();
		int id;

		try {
			id = app.beginBlock(block.getDescription().getHeight(), previous.getStateId(), node.getBlockchain().creationTimeOf(block));
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
				catch (RejectedTransactionException e) {
					throw new VerificationException("Failed check of transaction " + tx.getHexHash(node.getHasherForTransactions()) + ": " + e.getMessage());
				}

				try {
					app.deliverTransaction(tx, id);
				}
				catch (RejectedTransactionException e) {
					throw new VerificationException("Failed delivery of transaction " + tx.getHexHash(node.getHasherForTransactions()) + ": " + e.getMessage());
				}
			}

			var expected = app.endBlock(id, block.getDeadline());

			if (!Arrays.equals(block.getStateId(), expected))
				throw new VerificationException("Final state mismatch (expected " + Hex.toHexString(expected) + " but found " + Hex.toHexString(block.getStateId()) + ")");

			success = true;
		}
		finally {
			if (success && commit)
				app.commitBlock(id);
			else
				app.abortBlock(id);
		}
	}

	private void finalStateIsTheInitialStateOfTheApplication() throws VerificationException, TimeoutException, InterruptedException {
		var expected = node.getApplication().getInitialStateId();
		if (!Arrays.equals(block.getStateId(), expected))
			throw new VerificationException("Final state mismatch (expected " + Hex.toHexString(expected) + " but found " + Hex.toHexString(block.getStateId()) + ")");
	}
}