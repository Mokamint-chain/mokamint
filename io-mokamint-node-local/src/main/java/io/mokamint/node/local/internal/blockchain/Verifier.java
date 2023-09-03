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
import io.mokamint.node.local.internal.ClosedDatabaseException;
import io.mokamint.node.local.internal.LocalNodeImpl;

/**
 * A verifier of the consensus rules of the blocks that gets added to a blockchain.
 */
public class Verifier {

	/**
	 * The node having the blockchain whose blocks get verified.
	 */
	private final LocalNodeImpl node;

	/**
	 * Created a new verifier.
	 * 
	 * @param node the node whose blocks get verified
	 */
	Verifier(LocalNodeImpl node) {
		this.node = node;
	}

	/**
	 * Verifies if {@code block} satisfies all consensus rules required for being a child of {@code previous}.
	 * 
	 * @param block the block
	 * @param previous the previous of {@code block}; this can be empty only if {@code block} is a genesis block
	 * @throws VerificationException if verification fails
	 * @throws ClosedDatabaseException if the database is already closed
	 * @throws DatabaseException if the database is corrupted
	 */
	public void verify(Block block, Optional<Block> previous) throws VerificationException, DatabaseException, ClosedDatabaseException {
		if (block instanceof GenesisBlock gb)
			verify(gb);
		else
			verify((NonGenesisBlock) block, previous.get());
	}

	/**
	 * Verifies if a genesis block is valid for this blockchain.
	 * 
	 * @param genesis the genesis block
	 * @throws VerificationException if verification fails
	 * @throws ClosedDatabaseException if the database is already closed
	 * @throws DatabaseException if the database is corrupted
	 */
	private void verify(GenesisBlock genesis) throws VerificationException, DatabaseException, ClosedDatabaseException {
		creationTimeIsNotTooMuchInTheFuture(genesis);
	}

	/**
	 * Verifies if {@code block} satisfies all consensus rules required for being a child of {@code previous}.
	 * This method is called only if the database is not empty. Namely, if {@code previous} is in the
	 * database and if the genesis block of the database is set. It is guaranteed that the
	 * previous hash inside {@code block} coincides with the hash of {@code previous} (hence
	 * that condition is not verified by this method).
	 * 
	 * @param block the block
	 * @param previous the previous block
	 * @throws VerificationException if verification fails
	 * @throws ClosedDatabaseException if the database is already closed
	 * @throws DatabaseException if the database is corrupted
	 */
	private void verify(NonGenesisBlock block, Block previous) throws VerificationException, DatabaseException, ClosedDatabaseException {
		heightIsCorrect(block, previous);
		creationTimeIsNotTooMuchInTheFuture(block);
	}

	/**
	 * Checks the the height of {@code block} is one more than the height of {@code previous}.
	 * 
	 * @param block the block
	 * @param previous the previous of {@code block}
	 * @throws VerificationException if that condition in violated
	 */
	private void heightIsCorrect(NonGenesisBlock block, Block previous) throws VerificationException {
		long expected = previous.getHeight() + 1;
		if (block.getHeight() != expected)
			throw new VerificationException("Height mismatch (expected " + expected + " but found " + block.getHeight() + ")");
	}

	/**
	 * Yields the creation time of the given block.
	 * 
	 * @param block the block
	 * @return the creation time of {@code block}
	 * @throws ClosedDatabaseException if the database is already closed
	 * @throws DatabaseException if the database is corrupted
	 */
	private LocalDateTime creationTimeOf(Block block) throws DatabaseException, ClosedDatabaseException {
		if (block instanceof GenesisBlock gb)
			return gb.getStartDateTimeUTC();
		else
			return node.getBlockchain().getGenesis()
				.orElseThrow(() -> new DatabaseException("The database is not empty but its genesis block is not set"))
				.getStartDateTimeUTC().plus(block.getTotalWaitingTime(), ChronoUnit.MILLIS);
	}

	/**
	 * Checks that the creation time of the given block is not too much in the future.
	 * 
	 * @param block the block
	 * @throws VerificationException if the creationTime of {@code block} is too much in the future
	 * @throws ClosedDatabaseException if the database is already closed
	 * @throws DatabaseException if the database is corrupted
	 */
	private void creationTimeIsNotTooMuchInTheFuture(Block block) throws VerificationException, DatabaseException, ClosedDatabaseException {
		LocalDateTime now = node.getPeers().asNetworkDateTime(LocalDateTime.now(ZoneId.of("UTC")));
		long howMuchInTheFuture = ChronoUnit.MILLIS.between(now, creationTimeOf(block));
		long max = node.getConfig().blockMaxTimeInTheFuture;
		if (howMuchInTheFuture > max)
			throw new VerificationException("Too much in the future (" + howMuchInTheFuture + " ms against an allowed maximum of " + max + " ms)");
	}
}