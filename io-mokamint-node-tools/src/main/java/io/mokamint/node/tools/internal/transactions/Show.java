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

import java.security.NoSuchAlgorithmException;
import java.util.concurrent.TimeoutException;

import com.google.gson.Gson;

import io.hotmoka.crypto.Hex;
import io.hotmoka.crypto.HexConversionException;
import io.mokamint.node.api.ClosedNodeException;
import io.mokamint.node.api.DatabaseException;
import io.mokamint.node.api.RejectedTransactionException;
import io.mokamint.node.remote.api.RemotePublicNode;
import io.mokamint.node.tools.internal.AbstractPublicRpcCommand;
import io.mokamint.tools.CommandException;
import picocli.CommandLine.Command;
import picocli.CommandLine.Parameters;

@Command(name = "show", description = "Show a transaction in the blockchain of a node.")
public class Show extends AbstractPublicRpcCommand {

	@Parameters(index = "0", description = "the hexadecimal hash of the transaction")
	private String hash;

	private void body(RemotePublicNode remote) throws TimeoutException, InterruptedException, ClosedNodeException, CommandException, DatabaseException {
		try {
			var maybeRepresentation = remote.getTransactionRepresentation(toBytes(hash));
			if (maybeRepresentation.isEmpty())
				throw new CommandException("The blockchain of the node does not contain any transaction with that hash!");

			if (json()) {
				var answer = new Answer();
				answer.representation = maybeRepresentation.get();

				var gson = new Gson();
				System.out.println(gson.toJsonTree(answer));
			}
			else
				System.out.println(maybeRepresentation.get());
		}
		catch (NoSuchAlgorithmException e) {
			throw new CommandException("Unknown cryptographical algorithm in a block of \"" + publicUri() + "\"", e);
		}
		catch (RejectedTransactionException e) {
			throw new CommandException("The transaction exists in blockchain but cannot be transformed into its textual representation", e);
		}
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

	private static class Answer {
		@SuppressWarnings("unused")
		private String representation;
	}
}