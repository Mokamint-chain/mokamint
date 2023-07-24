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

package io.mokamint.node.tools.internal.blocks;

import java.security.NoSuchAlgorithmException;
import java.util.Optional;
import java.util.concurrent.TimeoutException;
import java.util.logging.Level;
import java.util.logging.Logger;

import io.hotmoka.crypto.Hex;
import io.mokamint.node.Blocks;
import io.mokamint.node.api.Block;
import io.mokamint.node.api.ClosedNodeException;
import io.mokamint.node.api.DatabaseException;
import io.mokamint.node.api.GenesisBlock;
import io.mokamint.node.api.NonGenesisBlock;
import io.mokamint.node.remote.RemotePublicNode;
import io.mokamint.node.tools.internal.AbstractPublicRpcCommand;
import jakarta.websocket.EncodeException;
import picocli.CommandLine.ArgGroup;
import picocli.CommandLine.Command;
import picocli.CommandLine.Help.Ansi;
import picocli.CommandLine.Option;

@Command(name = "show", description = "Show the blocks of the chain of a node.")
public class Show extends AbstractPublicRpcCommand {

	@ArgGroup(exclusive = true, multiplicity = "1")
	private BlockIdentifier blockIdentifier;

	/**
	 * The alternative ways of specifying the block to show.
	 */
	private static class BlockIdentifier {
        @Option(names = "hash", required = true, description = "the block with the given hexadecimal hash (not necessarily in the current chain)") String hash;
        @Option(names = "head", required = true, description = "the head of the current chain") boolean head;
        @Option(names = "genesis", required = true, description = "the genesis of the current chain") boolean genesis;
        @Option(names = "depth", required = true, description = "the block of the current chain at the given depth (0 for the head, 1 for the block below it, etc)") long depth;

        /**
         * Yields the specified block, if any.
         * 
         * @param remote the remote node
         * @return the block, if any
         * @throws NoSuchAlgorithmException if some block uses an unknown hashing algorithm
         * @throws DatabaseException if the database of the remote node is corrupted
         * @throws TimeoutException if some connection timed-out
         * @throws InterruptedException if some connection was interrupted while waiting
         * @throws ClosedNodeException if the remote node is closed
         */
        private Optional<Block> getBlock(RemotePublicNode remote) throws NoSuchAlgorithmException, DatabaseException, TimeoutException, InterruptedException, ClosedNodeException {
        	if (hash != null) {
				if (hash.startsWith("0x") || hash.startsWith("0X"))
					hash = hash.substring(2);

				Optional<Block> result = remote.getBlock(Hex.fromHexString(hash));
				if (result.isPresent())
					return result;
				else
					System.out.println(Ansi.AUTO.string("@|red The node does not contain any block with hash " + hash + "|@"));
			}
			else if (head) {
				var info = remote.getChainInfo();
				var headHash = info.getHeadHash();
				if (headHash.isPresent())
					return Optional.of(remote.getBlock(headHash.get()).orElseThrow(() -> new DatabaseException("The node has a head hash but it is bound to no block!")));
				else
					System.out.println(Ansi.AUTO.string("@|red There is no chain head in the node!|@"));
			}
			else if (genesis) {
				var info = remote.getChainInfo();
				var genesisHash = info.getGenesisHash();
				if (genesisHash.isPresent())
					return Optional.of(remote.getBlock(genesisHash.get()).orElseThrow(() -> new DatabaseException("The node has a genesis hash but it is bound to no block!")));
				else
					System.out.println(Ansi.AUTO.string("@|red There is no genesis block in the node!|@"));
			}
			else {
				// it must be --depth, since the {@code blockIdentifier} parameter is mandatory
				if (depth < 0)
					System.out.println(Ansi.AUTO.string("@|red The depth of the block must be positive|@"));
				else if (depth > 20)
					System.out.println(Ansi.AUTO.string("@|red Cannot show more than 20 blocks behind the head|@"));
				else {
					var info = remote.getChainInfo();
					var maybeHeadhHash = info.getHeadHash();
					if (maybeHeadhHash.isPresent()) {
						Optional<Block> maybeHead = remote.getBlock(maybeHeadhHash.get());
						if (maybeHead.isPresent()) {
							var head = maybeHead.get();
							var height = head.getHeight();
							if (height - depth < 0)
								System.out.println(Ansi.AUTO.string("@|red There is no block at that depth since the chain has height " + height + "!|@"));
							else
								return Optional.of(backwards(head, depth, remote));
						}
						else
							throw new DatabaseException("The node has a head hash but it is bound to no block!");
					}
					else
						System.out.println(Ansi.AUTO.string("@|red There is no block at that depth since the chain has no head!|@"));
				}
			}

        	return Optional.empty();
        }

        /**
         * Goes {@code depth} blocks backwards from the given cursor.
         * 
         * @param cursor the starting block
         * @param depth how much backwards it should go
         * @param the remote node
         * @return the resulting block
         * @throws NoSuchAlgorithmException if some block uses an unknown hashing algorithm
         * @throws DatabaseException if the database of the remote node is corrupted
         * @throws TimeoutException if some connection timed-out
         * @throws InterruptedException if some connection was interrupted while waiting
         * @throws ClosedNodeException if the remote node is closed
         */
		private Block backwards(Block cursor, long depth, RemotePublicNode remote) throws NoSuchAlgorithmException, TimeoutException, InterruptedException, DatabaseException, ClosedNodeException {
			if (depth == 0)
				return cursor;
			else if (cursor instanceof NonGenesisBlock ngb) {
				var previousHash = ngb.getHashOfPreviousBlock();
				Optional<Block> maybePrevious = remote.getBlock(previousHash);
				if (maybePrevious.isPresent())
					return backwards(maybePrevious.get(), depth - 1, remote);
				else {
					var config = remote.getConfig();
					throw new DatabaseException("Block " + Hex.toHexString(cursor.getHash(config.getHashingForBlocks())) + " has a previous hash that does not refer to any existing block!");
				}
			}
			else {
				var config = remote.getConfig();
				throw new DatabaseException("Block " + Hex.toHexString(cursor.getHash(config.getHashingForBlocks())) + " is a genesis block but is not at height 0!");
			}
		}
	}

    private final static Logger LOGGER = Logger.getLogger(Show.class.getName());

    private void body(RemotePublicNode remote) throws TimeoutException, InterruptedException, ClosedNodeException {
		try {
			var block = blockIdentifier.getBlock(remote);
			if (block.isPresent())
				print(remote, block.get());
		}
		catch (NoSuchAlgorithmException e) {
			System.out.println(Ansi.AUTO.string("@|red Some block uses an unknown hashing algorithm!|@"));
			LOGGER.log(Level.SEVERE, "unknown hashing algotihm in a block at \"" + publicUri() + "\"", e);
		}
		catch (EncodeException e) {
			System.out.println(Ansi.AUTO.string("@|red Cannot encode in JSON format!|@"));
			LOGGER.log(Level.SEVERE, "cannot encode a block from \"" + publicUri() + "\" in JSON format.", e);
		}
		catch (DatabaseException e) {
			System.out.println(Ansi.AUTO.string("@|red The database of the node at \"" + publicUri() + "\" seems corrupted!|@"));
			LOGGER.log(Level.SEVERE, "error accessing the database of the node at \"" + publicUri() + "\".", e);
		}
	}

    private void print(RemotePublicNode remote, Block block) throws EncodeException, NoSuchAlgorithmException, DatabaseException, TimeoutException, InterruptedException, ClosedNodeException {
    	if (json())
			System.out.println(new Blocks.Encoder().encode(block));
		else {
			var info = remote.getChainInfo();
			var genesisHash = info.getGenesisHash();
			if (genesisHash.isPresent()) {
				var config = remote.getConfig();
				var genesis = remote.getBlock(genesisHash.get());
				if (genesis.isPresent()) {
					var content = genesis.get();
					if (content instanceof GenesisBlock)
						System.out.println(block.toString(config, ((GenesisBlock) content).getStartDateTimeUTC()));
					else
						throw new DatabaseException("The initial block of the chain is not a genesis block!");
				}
				else
					System.out.println(block);
			}
			else
				System.out.println(block);
		}	
    }

    @Override
	protected void execute() {
		execute(this::body, LOGGER);
	}
}