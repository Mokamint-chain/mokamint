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
			else if (head)
				return getBlockAt(remote, remote.getChainInfo().getHeadHash(), "There is no chain head in the node!", "The node has a head hash but it is bound to no block!");
			else if (genesis)
				return getBlockAt(remote, remote.getChainInfo().getGenesisHash(), "There is no genesis block in the node!", "The node has a genesis hash but it is bound to no block!");
			else {
				// it must be --depth, since the {@code blockIdentifier} parameter is mandatory
				if (depth < 0)
					throw new CommandException("The depth of the block cannot be negative!");
				else {
					var length = remote.getChainInfo().getLength();
					if (length <= depth)
						throw new CommandException("There is no block at depth " + depth + " since the chain has length " + length + "!");
					else
						return getBlockAt(remote, remote.getChain(length - depth - 1, 1).getHashes().findFirst(),
							"There node cannot find the hash of the block at depth " + depth + "!",
							"The node contains a hash for the block at depth " + depth + " but it is bound to no block!");
				}
			}
        }

        private Block getBlockAt(RemotePublicNode remote, Optional<byte[]> hash, String ifEmpty, String ifMissing) throws NoSuchAlgorithmException, DatabaseException, TimeoutException, InterruptedException, ClosedNodeException, CommandException {
        	if (hash.isPresent())
				return remote.getBlock(hash.get()).orElseThrow(() -> new DatabaseException(ifMissing));
			else
				throw new CommandException(ifEmpty);
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
				var genesis = remote.getBlock(genesisHash.get()); // TODO: in the future, maybe a getBlockDescription() ?
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