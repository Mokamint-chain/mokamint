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

package io.mokamint.node.cli.internal.chain;

import java.util.Optional;
import java.util.concurrent.TimeoutException;

import io.hotmoka.cli.CommandException;
import io.hotmoka.crypto.Hex;
import io.hotmoka.crypto.HexConversionException;
import io.mokamint.node.BlockDescriptions;
import io.mokamint.node.Blocks;
import io.mokamint.node.DatabaseException;
import io.mokamint.node.api.Block;
import io.mokamint.node.api.BlockDescription;
import io.mokamint.node.api.GenesisBlockDescription;
import io.mokamint.node.api.NodeException;
import io.mokamint.node.cli.internal.AbstractPublicRpcCommand;
import io.mokamint.node.remote.api.RemotePublicNode;
import jakarta.websocket.EncodeException;
import picocli.CommandLine.ArgGroup;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

@Command(name = "show", description = "Show the blocks of the chain of a node.")
public class Show extends AbstractPublicRpcCommand {

	@ArgGroup(exclusive = true, multiplicity = "1")
	private BlockIdentifier blockIdentifier;

	@Option(names = "--full", description = "show full information about the block", defaultValue = "false")
	private boolean full;

	/**
	 * The alternative ways of specifying the block to show.
	 */
	private static class BlockIdentifier {
		@Option(names = "depth", required = true, description = "the block of the current chain at the given depth (0 is the head, 1 is the block below it, etc)") long depth;
		@Option(names = "genesis", required = true, description = "the genesis of the current chain") boolean genesis;
        @Option(names = "hash", required = true, description = "the block with the given hexadecimal hash (not necessarily in the current chain)") String hash;
        @Option(names = "head", required = true, description = "the head of the current chain") boolean head;
        @Option(names = "height", required = true, description = "the block of the current chain at the given height (0 is the genesis block, 1 is the block above it, etc)") Long height;

        /**
         * Yields hash of the requested block, if any.
         * 
         * @param remote the remote node
         * @return the hash of the requested block
         * @throws TimeoutException if some connection timed-out
         * @throws InterruptedException if some connection was interrupted while waiting
         * @throws NodeException if the remote node could not complete the operation
         * @throws CommandException if the block cannot be identified, or if something erroneous must be logged and the user must be informed
         */
        private byte[] getHashOfBlock(RemotePublicNode remote) throws TimeoutException, InterruptedException, NodeException, CommandException {
        	if (hash != null) {
				if (hash.startsWith("0x") || hash.startsWith("0X"))
					hash = hash.substring(2);

				try {
					return Hex.fromHexString(hash);
				}
				catch (HexConversionException e) {
					throw new CommandException("The hexadecimal hash is invalid!", e);
				}
			}
			else if (head)
				return remote.getChainInfo().getHeadHash().orElseThrow(() -> new CommandException("There is no chain head in the node!"));
			else if (genesis)
				return remote.getChainInfo().getGenesisHash().orElseThrow(() -> new CommandException("There is no genesis block in the node!"));
			else if (height != null && height < 0)
				throw new CommandException("The height of the block cannot be negative!");
			else if (height != null) {
				var length = remote.getChainInfo().getLength();
				if (length <= height)
					throw new CommandException("There is no block at height " + height + " since the chain has length " + length + "!");
				else
					return remote.getChainPortion(height, 1).getHashes().findFirst().orElseThrow(() -> new CommandException("The node cannot find the hash of the block at height " + height + "!"));
			}
			// it must be --depth, since the {@code blockIdentifier} parameter is mandatory
			else if (depth < 0)
				throw new CommandException("The depth of the block cannot be negative!");
			else {
				var length = remote.getChainInfo().getLength();
				if (length <= depth)
					throw new CommandException("There is no block at depth " + depth + " since the chain has length " + length + "!");
				else
					return remote.getChainPortion(length - depth - 1, 1).getHashes().findFirst().orElseThrow(() -> new CommandException("The node cannot find the hash of the block at depth " + depth + "!"));
			}
        }
	}

    private void body(RemotePublicNode remote) throws TimeoutException, InterruptedException, NodeException, CommandException {
		try {
			byte[] hash = blockIdentifier.getHashOfBlock(remote);
			if (full)
				print(remote, remote.getBlock(hash).orElseThrow(() -> new CommandException("The node does not contain any block with the requested hash!")), hash);
			else
				print(remote, remote.getBlockDescription(hash).orElseThrow(() -> new CommandException("The node does not contain any block with the requested hash!")), hash);
		}
		catch (EncodeException e) {
			throw new CommandException("Cannot encode a block from \"" + publicUri() + "\" in JSON format!", e);
		}
	}

    private void print(RemotePublicNode remote, BlockDescription description, byte[] hash) throws EncodeException, TimeoutException, InterruptedException, NodeException {
    	if (json())
    		System.out.println(new BlockDescriptions.Encoder().encode(description));
		else {
			var info = remote.getChainInfo();
			var genesisHash = info.getGenesisHash();
			if (genesisHash.isPresent()) {
				var genesis = remote.getBlockDescription(genesisHash.get());
				if (genesis.isPresent()) {
					var content = genesis.get();
					if (content instanceof GenesisBlockDescription gbd) {
						var config = remote.getConfig();
						System.out.println("* hash: " + Hex.toHexString(hash) + " (" + config.getHashingForBlocks() + ")");
						System.out.println(description.toString(Optional.of(config), Optional.of(gbd.getStartDateTimeUTC())));
					}
					else
						throw new DatabaseException("The initial block of the chain is not a genesis block!");
				}
				else
					System.out.println(description);
			}
			else
				System.out.println(description);
		}	
    }

    private void print(RemotePublicNode remote, Block block, byte[] hash) throws EncodeException, TimeoutException, InterruptedException, NodeException {
    	if (json())
    		System.out.println(new Blocks.Encoder().encode(block));
		else {
			var info = remote.getChainInfo();
			var genesisHash = info.getGenesisHash();
			if (genesisHash.isPresent()) {
				var genesis = remote.getBlockDescription(genesisHash.get());
				if (genesis.isPresent()) {
					var content = genesis.get();
					if (content instanceof GenesisBlockDescription gbd) {
						var config = remote.getConfig();
						System.out.println(block.toString(Optional.of(config), Optional.of(gbd.getStartDateTimeUTC())));
					}
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