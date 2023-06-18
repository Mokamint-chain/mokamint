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
import java.util.concurrent.TimeoutException;
import java.util.logging.Level;
import java.util.logging.Logger;

import io.hotmoka.crypto.Hex;
import io.mokamint.node.api.Block;
import io.mokamint.node.api.ConsensusConfig;
import io.mokamint.node.api.NonGenesisBlock;
import io.mokamint.node.remote.RemotePublicNode;
import io.mokamint.node.tools.internal.AbstractPublicRpcCommand;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Help.Ansi;

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
			var maybeHeadHash = remote.getChainInfo().getHeadHash();
			if (maybeHeadHash.isEmpty())
				return;

			var maybeHead = remote.getBlock(maybeHeadHash.get());
			if (maybeHead.isEmpty())
				throw new IOException("The node has a head hash but it is bound to no block!");

			Block head = maybeHead.get();
			int slotsForHeight = String.valueOf(head.getHeight()).length();
			if (json())
				System.out.print("[");
			backwards(head, slotsForHeight, remote.getConfig(), remote);
			if (json())
				System.out.println("]");
		}
		catch (NoSuchAlgorithmException e) {
			System.out.println(Ansi.AUTO.string("@|red The head of the chain uses an unknown hashing algorithm!|@"));
			LOGGER.log(Level.SEVERE, "unknown hashing algorithm in the head of the chain of the node at \"" + publicUri() + "\".", e);
		}
		catch (IOException e) {
			System.out.println(Ansi.AUTO.string("@|red The database of the node at \"" + publicUri() + "\" seems corrupted!|@"));
			LOGGER.log(Level.SEVERE, "error accessing the database of the node at \"" + publicUri() + "\".", e);
		}
	}

    /**
     * Goes {@code depth} blocks backwards from the given cursor, printing the hashes of the blocks on the way.
     * 
     * @param cursor the starting block
     * @param slotsForHeight the number of characters reserved for printing the height of each hash
     * @param config the configuration of the remote node
     * @param the remote node
     * @throws NoSuchAlgorithmException if some block uses an unknown hashing algorithm
     * @throws IOException if the database of the remote node is corrupted
     * @throws TimeoutException if some connection timed-out
     * @throws InterruptedException if some connection was interrupted while waiting
     */
	private void backwards(Block cursor, int slotsForHeight, ConsensusConfig config, RemotePublicNode remote) throws NoSuchAlgorithmException, TimeoutException, InterruptedException, IOException {
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
				System.out.println(String.format("%" + slotsForHeight + "d: %s", cursor.getHeight(), hash));

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
					throw new IOException("Block " + hash + " has a previous hash that does not refer to any existing block!");
				}
			}
			else {
				throw new IOException("Block " + hash + " is a genesis block but its is not at height 0!");
			}
		}
	}

	@Override
	protected void execute() {
		execute(this::body, LOGGER);
	}
}