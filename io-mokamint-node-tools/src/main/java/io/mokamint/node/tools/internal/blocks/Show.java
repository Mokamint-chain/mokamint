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
import io.mokamint.node.remote.RemotePublicNode;
import io.mokamint.node.tools.internal.AbstractRpcCommand;
import jakarta.websocket.EncodeException;
import picocli.CommandLine.ArgGroup;
import picocli.CommandLine.Command;
import picocli.CommandLine.Help.Ansi;
import picocli.CommandLine.Option;

@Command(name = "show", description = "Show the blocks of a node.")
public class Show extends AbstractRpcCommand {

	@ArgGroup(exclusive = true, multiplicity = "1")
	private BlockIdentifier blockIdentifier;

	static class BlockIdentifier {
        @Option(names = "--hash", required = true, description = "the block with the given hexadecimal hash (not necessarily in the current chain)") String hash;
        @Option(names = "--head", required = true, description = "the head of the current chain") boolean head;
        @Option(names = "--genesis", required = true, description = "the genesis of the current chain") boolean genesis;
        @Option(names = "--height", required = true, description = "the block of the current chain at the given height (0 for the genesis, 1 for the block above it, etc)") long height;
        @Option(names = "--depth", required = true, description = "the block of the current chain at the given depth (0 for the head, 1 for the block below it, etc)") long depth;
    }

    private final static Logger LOGGER = Logger.getLogger(Show.class.getName());

    private void body(RemotePublicNode remote) throws TimeoutException, InterruptedException {
		try {
			String hash = blockIdentifier.hash;
			if (hash != null) {
				if (hash.startsWith("0x") || hash.startsWith("0X"))
					hash = hash.substring(2);

				Optional<Block> result = remote.getBlock(Hex.fromHexString(hash));
				if (result.isPresent()) {
					Block block = result.get();
					if (json())
						System.out.println(new Blocks.Encoder().encode(block));
					else
						System.out.println(block);
				}
				else
					System.out.println(Ansi.AUTO.string("@|red The node does not contain any block with hash " + hash + "|@"));
			}
		}
		catch (NoSuchAlgorithmException e) {
			System.out.println(Ansi.AUTO.string("@|red The block uses an unknown hashing algorithm!|@"));
			LOGGER.log(Level.SEVERE, "unknown hashing algotihm in a block at \"" + publicUri() + "\"", e);
		}
		catch (EncodeException e) {
			System.out.println(Ansi.AUTO.string("@|red Cannot encode in JSON format!|@"));
			LOGGER.log(Level.SEVERE, "cannot encode a block from \"" + publicUri() + "\" in JSON format.", e);
		}
	}

    @Override
	protected void execute() {
		executeOnPublicAPI(this::body, LOGGER);
	}
}