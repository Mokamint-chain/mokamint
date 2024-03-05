/*
Copyright 2024 Fausto Spoto

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

package io.mokamint.node.cli.internal.transactions;

import java.util.concurrent.TimeoutException;

import io.hotmoka.crypto.Hex;
import io.hotmoka.crypto.HexConversionException;
import io.hotmoka.websockets.beans.EncodeException;
import io.mokamint.cli.CommandException;
import io.mokamint.node.TransactionAddresses;
import io.mokamint.node.api.DatabaseException;
import io.mokamint.node.api.NodeException;
import io.mokamint.node.api.TransactionAddress;
import io.mokamint.node.cli.internal.AbstractPublicRpcCommand;
import io.mokamint.node.remote.api.RemotePublicNode;
import picocli.CommandLine.Command;
import picocli.CommandLine.Parameters;

@Command(name = "find", description = "Find a transaction in the blockchain of a node.")
public class Find extends AbstractPublicRpcCommand {

	@Parameters(index = "0", description = "the hexadecimal hash of the transaction")
	private String hash;

	private void body(RemotePublicNode remote) throws TimeoutException, InterruptedException, NodeException, CommandException, DatabaseException {
		var address = getTransactionAddress(remote);

		if (json()) {
			try {
				System.out.println(new TransactionAddresses.Encoder().encode(address));
			}
			catch (EncodeException e) {
				throw new CommandException("Cannot encode an address at \"" + publicUri() + "\" in JSON format!", e);
			}
		}
		else
			System.out.println(address);
	}

	private TransactionAddress getTransactionAddress(RemotePublicNode remote) throws TimeoutException, InterruptedException, NodeException, DatabaseException, CommandException {
		return remote.getTransactionAddress(toBytes(hash)).orElseThrow(() -> new CommandException("The blockchain of the node does not contain any transaction with that hash!"));
	}

	@Override
	protected void execute() throws CommandException {
		execute(this::body);
	}

	private static byte[] toBytes(String hash) throws CommandException {
		if (hash.startsWith("0x") || hash.startsWith("0X"))
			hash = hash.substring(2);

		try {
			return Hex.fromHexString(hash);
		}
		catch (HexConversionException e) {
			throw new CommandException("The hexadecimal hash is invalid!", e);
		}
	}
}