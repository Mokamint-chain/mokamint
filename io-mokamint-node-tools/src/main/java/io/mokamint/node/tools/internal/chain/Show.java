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
import java.util.Optional;
import java.util.concurrent.TimeoutException;

import io.hotmoka.crypto.Hex;
import io.hotmoka.crypto.HexConversionException;
import io.mokamint.node.Blocks;
import io.mokamint.node.api.Block;
import io.mokamint.node.api.ClosedNodeException;
import io.mokamint.node.api.DatabaseException;
import io.mokamint.node.api.GenesisBlock;
import io.mokamint.node.api.NonGenesisBlock;
import io.mokamint.node.remote.api.RemotePublicNode;
import io.mokamint.node.tools.internal.AbstractPublicRpcCommand;
import io.mokamint.tools.CommandException;
import jakarta.websocket.EncodeException;
import picocli.CommandLine.ArgGroup;
import picocli.CommandLine.Command;
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
        @Option(names = "depth", required = true, description = "the block of the current chain at the given depth (0 is the head, 1 is the block below it, etc)") long depth;

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
         * @throws CommandException if something erroneous must be logged and the user must be informed
         */
        private Block getBlock(RemotePublicNode remote) throws NoSuchAlgorithmException, DatabaseException, TimeoutException, InterruptedException, ClosedNodeException, CommandException {
        	if (hash != null) {
				if (hash.startsWith("0x") || hash.startsWith("0X"))
					hash = hash.substring(2);

				try {
					return remote.getBlock(Hex.fromHexString(hash)).orElseThrow(() -> new CommandException("The node does not contain any block with hash " + hash + "!"));
				}
				catch (HexConversionException e) {
					throw new CommandException("The hexadecimal hash is invalid!", e);
				}
			}
			else if (head) {
				var info = remote.getChainInfo();
				var headHash = info.getHeadHash();
				if (headHash.isPresent())
					return remote.getBlock(headHash.get()).orElseThrow(() -> new DatabaseException("The node has a head hash but it is bound to no block!"));
				else
					throw new CommandException("There is no chain head in the node!");
			}
			else if (genesis) {
				var info = remote.getChainInfo();
				var genesisHash = info.getGenesisHash();
				if (genesisHash.isPresent())
					return remote.getBlock(genesisHash.get()).orElseThrow(() -> new DatabaseException("The node has a genesis hash but it is bound to no block!"));
				else
					throw new CommandException("There is no genesis block in the node!");
			}
			else {
				// it must be --depth, since the {@code blockIdentifier} parameter is mandatory
				if (depth < 0)
					throw new CommandException("The depth of the block must be positive!");
				else if (depth > 20)
					throw new CommandException("Cannot show more than 20 blocks behind the head!");
				else {
					var info = remote.getChainInfo();
					var maybeHeadHash = info.getHeadHash();
					if (maybeHeadHash.isPresent()) {
						var head = remote.getBlock(maybeHeadHash.get()).orElseThrow(() -> new DatabaseException("The node has a head hash but it is bound to no block!"));
						var height = head.getHeight();
						if (height - depth < 0)
							throw new CommandException("There is no block at depth " + depth + " since the chain has height " + height + "!");
						else
							return backwards(head, depth, remote);
					}
					else
						throw new CommandException("There is no block at depth " + depth + " since the chain has no head!");
				}
			}
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
				Optional<Block> maybePrevious = remote.getBlock(ngb.getHashOfPreviousBlock());
				if (maybePrevious.isPresent())
					return backwards(maybePrevious.get(), depth - 1, remote);
				else
					throw new DatabaseException("Block " + cursor.getHexHash(remote.getConfig().getHashingForBlocks()) + " has a previous hash that does not refer to any existing block!");
			}
			else
				throw new DatabaseException("Block " + cursor.getHexHash(remote.getConfig().getHashingForBlocks()) + " is a genesis block but is not at height 0!");
		}
	}

    private void body(RemotePublicNode remote) throws TimeoutException, InterruptedException, ClosedNodeException, DatabaseException, CommandException {
		try {
			print(remote, blockIdentifier.getBlock(remote));
		}
		catch (NoSuchAlgorithmException e) {
			throw new CommandException("Unknown hashing algorithm in a block of \"" + publicUri() + "\"", e);
		}
		catch (EncodeException e) {
			throw new CommandException("Cannot encode a block from \"" + publicUri() + "\" in JSON format!", e);
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
					if (content instanceof GenesisBlock gb)
						System.out.println(block.toString(config, gb.getStartDateTimeUTC()));
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
	protected void execute() throws CommandException {
		execute(this::body);
	}
}