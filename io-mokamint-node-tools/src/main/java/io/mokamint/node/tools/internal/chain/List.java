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

package io.mokamint.node.tools.internal.chain;

import java.io.IOException;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import java.util.concurrent.TimeoutException;
import java.util.logging.Level;
import java.util.logging.Logger;

import io.hotmoka.crypto.Hex;
import io.mokamint.node.api.Block;
import io.mokamint.node.api.ChainInfo;
import io.mokamint.node.api.ConsensusConfig;
import io.mokamint.node.api.DatabaseException;
import io.mokamint.node.api.GenesisBlock;
import io.mokamint.node.api.NonGenesisBlock;
import io.mokamint.node.remote.RemotePublicNode;
import io.mokamint.node.tools.internal.AbstractPublicRpcCommand;
import picocli.CommandLine.Command;
import picocli.CommandLine.Help.Ansi;
import picocli.CommandLine.Option;

@Command(name = "ls", description = "List the blocks in the chain of a node.")
public class List extends AbstractPublicRpcCommand {

	@Option(names = "--max", description = "the maximum number of blocks that can be listed", defaultValue = "100")
	private long max;

	private final static Logger LOGGER = Logger.getLogger(List.class.getName());

	private void body(RemotePublicNode remote) throws TimeoutException, InterruptedException {
		if (max < 0) {
			System.out.println(Ansi.AUTO.string("@|red max cannot be negative!|@"));
			return;
		}

		try {
			ChainInfo info = remote.getChainInfo();

			var maybeHeadHash = info.getHeadHash();
			if (maybeHeadHash.isEmpty())
				return;

			var maybeHead = remote.getBlock(maybeHeadHash.get());
			if (maybeHead.isEmpty())
				throw new DatabaseException("The node has a head hash but it is bound to no block!");

			Block head = maybeHead.get();

			var maybeGenesisHash = info.getGenesisHash();
			if (maybeGenesisHash.isEmpty())
				return;

			Optional<LocalDateTime> startDateTimeUTC;
			int slotsForHeight = 0;

			if (json())
				startDateTimeUTC = Optional.empty();
			else {
				var maybeGenesis = remote.getBlock(maybeGenesisHash.get());
				if (maybeGenesis.isEmpty())
					throw new DatabaseException("The node has a genesis hash but it is bound to no block!");

				Block genesis = maybeGenesis.get();
				if (!(genesis instanceof GenesisBlock))
					throw new DatabaseException("The type of the genesisi block is inconsistent!");

				slotsForHeight = String.valueOf(head.getHeight()).length();
				startDateTimeUTC = Optional.of(((GenesisBlock) genesis).getStartDateTimeUTC());
			}

			if (json())
				System.out.print("[");
			backwards(head, slotsForHeight, startDateTimeUTC, remote.getConfig(), remote);
			if (json())
				System.out.println("]");
		}
		catch (NoSuchAlgorithmException e) {
			System.out.println(Ansi.AUTO.string("@|red The head of the chain uses an unknown hashing algorithm!|@"));
			LOGGER.log(Level.SEVERE, "unknown hashing algorithm in the head of the chain of the node at \"" + publicUri() + "\".", e);
		}
		catch (DatabaseException e) {
			System.out.println(Ansi.AUTO.string("@|red The database of the node at \"" + publicUri() + "\" seems corrupted!|@"));
			LOGGER.log(Level.SEVERE, "error accessing the database of the node at \"" + publicUri() + "\".", e);
		}
	}

    /**
     * Goes {@code depth} blocks backwards from the given cursor, printing the hashes of the blocks on the way.
     * 
     * @param cursor the starting block
     * @param slotsForHeight the number of characters reserved for printing the height of each hash; this is used only if json() is false
     * @param startDateTimeUTC the starting moment of the chain; this is used only if json() is false
     * @param config the configuration of the remote node
     * @param the remote node
     * @throws NoSuchAlgorithmException if some block uses an unknown hashing algorithm
     * @throws IOException if the database of the remote node is corrupted
     * @throws TimeoutException if some connection timed-out
     * @throws InterruptedException if some connection was interrupted while waiting
     */
	private void backwards(Block cursor, int slotsForHeight, Optional<LocalDateTime> startDateTimeUTC, ConsensusConfig config, RemotePublicNode remote) throws NoSuchAlgorithmException, TimeoutException, InterruptedException, DatabaseException {
		long counter = 0;

		while (true) {
			if (counter >= max)
				return;

			String hash = Hex.toHexString(config.getHashingForBlocks().hash(cursor.toByteArray()));
			if (json()) {
				if (counter > 0)
					System.out.print(", ");

				System.out.print("\"" + hash + "\"");
			}
			else
				System.out.println(String.format("%" + slotsForHeight + "d: %s [%s]", cursor.getHeight(), hash, startDateTimeUTC.get().plus(cursor.getTotalWaitingTime(), ChronoUnit.MILLIS)));

			if (cursor.getHeight() == 0)
				return;
			else if (cursor instanceof NonGenesisBlock) {
				var ngb = (NonGenesisBlock) cursor;
				var previousHash = ngb.getHashOfPreviousBlock();
				var maybePrevious = remote.getBlock(previousHash);
				if (maybePrevious.isPresent()) {
					cursor = maybePrevious.get();
					counter++;
				}
				else {
					throw new DatabaseException("Block " + hash + " has a previous hash that does not refer to any existing block!");
				}
			}
			else {
				throw new DatabaseException("Block " + hash + " is a genesis block but its is not at height 0!");
			}
		}
	}

	@Override
	protected void execute() {
		execute(this::body, LOGGER);
	}
}