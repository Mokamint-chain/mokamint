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

package io.mokamint.node.local.internal.blockchain;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.Optional;

import io.mokamint.node.api.Block;
import io.mokamint.node.api.DatabaseException;
import io.mokamint.node.api.GenesisBlock;
import io.mokamint.node.api.NonGenesisBlock;
import io.mokamint.node.local.api.LocalNodeConfig;
import io.mokamint.node.local.internal.ClosedDatabaseException;
import io.mokamint.node.local.internal.LocalNodeImpl;
import io.mokamint.nonce.api.Deadline;
import io.mokamint.nonce.api.Prolog;

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
	 * @throws VerificationException if verification fails
	 * @throws ClosedDatabaseException if the database is already closed
	 * @throws DatabaseException if the database is corrupted
	 */
	BlockVerification(LocalNodeImpl node, Block block, Optional<Block> previous) throws VerificationException, DatabaseException, ClosedDatabaseException {
		this.node = node;
		this.config = node.getConfig();
		this.block = block;
		this.previous = previous.orElse(null);
		this.deadline = block instanceof NonGenesisBlock ngb ? ngb.getDeadline() : null;

		if (block instanceof GenesisBlock)
			verifyAsGenesis();
		else
			verifyAsNonGenesis();
	}

	/**
	 * Verifies if the genesis {@link #block} is valid for the blockchain in {@link #node}.
	 * 
	 * @throws VerificationException if verification fails
	 * @throws ClosedDatabaseException if the database is already closed
	 * @throws DatabaseException if the database is corrupted
	 */
	private void verifyAsGenesis() throws VerificationException, DatabaseException, ClosedDatabaseException {
		creationTimeIsNotTooMuchInTheFuture();
	}

	/**
	 * Verifies if the non-genesis {@link #block} satisfies all consensus rules required for being
	 * a child of {@link #previous}. This method is called only if the database is not empty.
	 * Namely, if {@link #previous} is in the database and if the genesis block of the database is set.
	 * It is guaranteed that the previous hash inside {@link #block} coincides with the hash
	 * of {@link #previous} (hence this condition is not verified by this method).
	 * 
	 * @throws VerificationException if verification fails
	 * @throws ClosedDatabaseException if the database is already closed
	 * @throws DatabaseException if the database is corrupted
	 */
	private void verifyAsNonGenesis() throws VerificationException, DatabaseException, ClosedDatabaseException {
		creationTimeIsNotTooMuchInTheFuture();
		deadlineMatchesItsExpectedDescription();
		deadlineHasValidProlog();
		deadlineIsValid();
		blockMatchesItsExpectedDescription();
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
	 */
	private void deadlineHasValidProlog() throws VerificationException {
		Prolog prolog = deadline.getProlog();

		if (!prolog.getChainId().equals(config.getChainId()))
			throw new VerificationException("Deadline prolog's chainId mismatch");

		if (!prolog.getSignatureForBlocks().equals(config.getSignatureForBlocks()))
			throw new VerificationException("Deadline prolog's signature algorithm for blocks mismatch");

		if (!prolog.getSignatureForDeadlines().equals(config.getSignatureForDeadlines()))
			throw new VerificationException("Deadline prolog's signature algorithm for deadlines mismatch");

		if (!node.getApplication().prologExtraIsValid(prolog.getExtra()))
			throw new VerificationException("Invalid deadline prolog's extra");
	}

	/**
	 * Checks if the deadline of {@link #block} matches its expected description.
	 * 
	 * @throws VerificationException if that condition in violated
	 */
	private void deadlineMatchesItsExpectedDescription() throws VerificationException {
		if (!deadline.matches(previous.getNextDeadlineDescription(config.getHashingForGenerations(), config.getHashingForDeadlines())))
			throw new VerificationException("Deadline mismatch");
	}

	/**
	 * Checks if {@link #block} matches its expected description.
	 * 
	 * @throws VerificationException if that condition in violated
	 */
	private void blockMatchesItsExpectedDescription() throws VerificationException {
		var description = previous.getNextBlockDescription(deadline, config.getTargetBlockCreationTime(), config.getHashingForBlocks(), config.getHashingForDeadlines());

		if (block.getHeight() != description.getHeight())
			throw new VerificationException("Height mismatch (expected " + description.getHeight() + " but found " + block.getHeight() + ")");

		if (!block.getAcceleration().equals(description.getAcceleration()))
			throw new VerificationException("Acceleration mismatch (expected " + description.getAcceleration() + " but found " + block.getAcceleration() + ")");

		if (!block.getPower().equals(description.getPower()))
			throw new VerificationException("Power mismatch (expected " + description.getPower() + " but found " + block.getPower() + ")");

		if (block.getTotalWaitingTime() != description.getTotalWaitingTime())
			throw new VerificationException("Total waiting time mismatch (expected " + description.getTotalWaitingTime() + " but found " + block.getTotalWaitingTime() + ")");

		if (block.getWeightedWaitingTime() != description.getWeightedWaitingTime())
			throw new VerificationException("Weighted waiting time mismatch (expected " + description.getWeightedWaitingTime() + " but found " + block.getWeightedWaitingTime() + ")");
	}

	/**
	 * Yields the creation time of {@link #block}.
	 * 
	 * @return the creation time of {@link #block}
	 * @throws ClosedDatabaseException if the database is already closed
	 * @throws DatabaseException if the database is corrupted
	 */
	private LocalDateTime creationTimeOfBlock() throws DatabaseException, ClosedDatabaseException {
		if (block instanceof GenesisBlock gb)
			return gb.getStartDateTimeUTC();
		else
			return node.getBlockchain().getGenesis()
				.orElseThrow(() -> new DatabaseException("The database is not empty but its genesis block is not set"))
				.getStartDateTimeUTC().plus(block.getTotalWaitingTime(), ChronoUnit.MILLIS);
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
		long howMuchInTheFuture = ChronoUnit.MILLIS.between(now, creationTimeOfBlock());
		long max = node.getConfig().getBlockMaxTimeInTheFuture();
		if (howMuchInTheFuture > max)
			throw new VerificationException("Too much in the future (" + howMuchInTheFuture + " ms against an allowed maximum of " + max + " ms)");
	}
}