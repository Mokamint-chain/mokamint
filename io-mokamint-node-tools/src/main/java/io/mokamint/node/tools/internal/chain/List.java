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

import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import java.util.concurrent.TimeoutException;
import java.util.logging.Logger;

import io.hotmoka.crypto.Hex;
import io.mokamint.node.api.BlockDescription;
import io.mokamint.node.api.Chain;
import io.mokamint.node.api.ClosedNodeException;
import io.mokamint.node.api.DatabaseException;
import io.mokamint.node.api.GenesisBlockDescription;
import io.mokamint.node.remote.api.RemotePublicNode;
import io.mokamint.node.tools.internal.AbstractPublicRpcCommand;
import io.mokamint.tools.CommandException;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

@Command(name = "ls", description = "List the blocks in the chain of a node.")
public class List extends AbstractPublicRpcCommand {

	@Parameters(description = "the number of blocks that must be listed", defaultValue = "100")
	private int count;

	@Option(names = "from", description = "the height of the first block that must be reported (-1 to list the topmost count blocks)", defaultValue = "-1")
	private long from;

	private final static Logger LOGGER = Logger.getLogger(List.class.getName());

	/**
	 * The formatter used to print the creation time of the blocks.
	 */
	private final static DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

	private void body(RemotePublicNode remote) throws TimeoutException, InterruptedException, ClosedNodeException, DatabaseException, CommandException {
		if (count < 0)
			throw new CommandException("count cannot be negative!");

		if (from < -1L)
			throw new CommandException("from cannot be smaller than -1!");

		try {
			var info = remote.getChainInfo();
			long height = info.getLength() - 1;
			if (height < 0)
				return;

			if (from == -1L)
				from = Math.max(0L, height - count + 1);

			LOGGER.info("requesting hashes in the height interval [" + from + ", " + (from + count) + ")");

			var maybeGenesisHash = info.getGenesisHash();
			if (maybeGenesisHash.isEmpty())
				return;

			Optional<LocalDateTime> startDateTimeUTC;
			int slotsForHeight;

			if (json()) {
				startDateTimeUTC = Optional.empty();
				slotsForHeight = 0;
			}
			else {
				var maybeGenesis = remote.getBlockDescription(maybeGenesisHash.get());
				if (maybeGenesis.isEmpty())
					throw new DatabaseException("The node has a genesis hash but it is bound to no block!");

				BlockDescription genesis = maybeGenesis.get();
				if (genesis instanceof GenesisBlockDescription gbd) {
					slotsForHeight = String.valueOf(height).length();
					startDateTimeUTC = Optional.of(gbd.getStartDateTimeUTC());
				}
				else
					throw new DatabaseException("The type of the genesis block is inconsistent!");
			}

			if (json())
				System.out.print("[");
			
			Chain chain = remote.getChain(from, count);
			list(chain, from + chain.getHashes().count() - 1, slotsForHeight, startDateTimeUTC, remote);
			if (json())
				System.out.println("]");
		}
		catch (NoSuchAlgorithmException e) {
			throw new CommandException("Unknown hashing algorithm in the head of the chain of the node at \"" + publicUri() + "\"!", e);
		}
	}

	/**
     * Lists the hashes in {@code chain}, reporting the time of creation of each block.
     * 
     * @param chain the segment of the current chain to list
     * @param height the height of the current chain
     * @param slotsForHeight the number of characters reserved for printing the height of each hash; this is used only if json() is false
     * @param startDateTimeUTC the starting moment of the chain; this is used only if json() is false
     * @param the remote node
	 * @throws DatabaseException if the database of the node is corrupted
     * @throws NoSuchAlgorithmException if some block uses an unknown hashing algorithm
     * @throws TimeoutException if some connection timed-out
     * @throws InterruptedException if some connection was interrupted while waiting
     * @throws ClosedNodeException if the remote node is closed
     */
	private void list(Chain chain, long height, int slotsForHeight, Optional<LocalDateTime> startDateTimeUTC, RemotePublicNode remote) throws NoSuchAlgorithmException, DatabaseException, TimeoutException, InterruptedException, ClosedNodeException {
		var hashes = chain.getHashes().toArray(byte[][]::new);

		for (int counter = hashes.length - 1; counter >= 0; counter--, height--) {
			String hash = Hex.toHexString(hashes[counter]);

			if (json()) {
				if (counter != hashes.length - 1)
					System.out.print(", ");

				System.out.print("\"" + hash + "\"");
			}
			else {
				var maybeBlockDescription = remote.getBlockDescription(hashes[counter]);
				String creationTime = maybeBlockDescription.map(BlockDescription::getTotalWaitingTime)
					.map(total -> startDateTimeUTC.get().plus(total, ChronoUnit.MILLIS).format(FORMATTER))
					.orElse("unknown");
				System.out.println(String.format("%" + slotsForHeight + "d: %s [%s]", height, hash, creationTime));
			}
		}
	}

	@Override
	protected void execute() throws CommandException {
		execute(this::body);
	}
}