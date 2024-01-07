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

package io.mokamint.node.tools.internal.transactions;

import java.util.concurrent.TimeoutException;

import io.hotmoka.crypto.Hex;
import io.hotmoka.crypto.HexConversionException;
import io.hotmoka.websockets.beans.EncodeException;
import io.mokamint.node.TransactionAddresses;
import io.mokamint.node.api.ClosedNodeException;
import io.mokamint.node.api.DatabaseException;
import io.mokamint.node.remote.api.RemotePublicNode;
import io.mokamint.node.tools.internal.AbstractPublicRpcCommand;
import io.mokamint.tools.CommandException;
import picocli.CommandLine.Command;
import picocli.CommandLine.Parameters;

@Command(name = "find", description = "Find a transaction in the blockchain of a node.")
public class Find extends AbstractPublicRpcCommand {

	@Parameters(index = "0", description = "the hexadecimal hash of the transaction")
	private String hash;

	private void body(RemotePublicNode remote) throws TimeoutException, InterruptedException, ClosedNodeException, CommandException, DatabaseException {
		var maybeAddress = remote.getTransactionAddress(toBytes(hash));
		if (maybeAddress.isEmpty())
			throw new CommandException("The blockchain of the node does not contain any transaction with that hash!");

		if (json()) {
			try {
				System.out.println(new TransactionAddresses.Encoder().encode(maybeAddress.get()));
			}
			catch (EncodeException e) {
				throw new CommandException("Cannot encode an address at \"" + publicUri() + "\" in JSON format!", e);
			}
		}
		else
			System.out.println(maybeAddress.get());
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