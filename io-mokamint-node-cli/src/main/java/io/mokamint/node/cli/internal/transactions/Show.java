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

import java.security.NoSuchAlgorithmException;
import java.util.concurrent.TimeoutException;

import com.google.gson.Gson;

import io.hotmoka.crypto.Hex;
import io.hotmoka.crypto.HexConversionException;
import io.mokamint.cli.CommandException;
import io.mokamint.node.Transactions;
import io.mokamint.node.api.DatabaseException;
import io.mokamint.node.api.NodeException;
import io.mokamint.node.api.RejectedTransactionException;
import io.mokamint.node.api.Transaction;
import io.mokamint.node.cli.internal.AbstractPublicRpcCommand;
import io.mokamint.node.remote.api.RemotePublicNode;
import jakarta.websocket.EncodeException;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

@Command(name = "show", description = "Show a transaction in the blockchain of a node.")
public class Show extends AbstractPublicRpcCommand {

	@Parameters(index = "0", description = "the hexadecimal hash of the transaction")
	private String hash;

	@Option(names = "--representation", description = "report the representation of the transaction instead of its Base64-encoded bytes", defaultValue = "false")
    private boolean representation;

	private void body(RemotePublicNode remote) throws TimeoutException, InterruptedException, NodeException, CommandException, DatabaseException {
		if (representation) {
			String representation = getTransactionRepresentation(remote);

			if (json()) {
				var answer = new Answer();
				answer.representation = representation;
				System.out.println(new Gson().toJsonTree(answer));
			}
			else
				System.out.println(representation);
		}
		else {
			var transaction = getTransaction(remote);

			if (json()) {
				try {
					System.out.println(new Transactions.Encoder().encode(transaction));
				}
				catch (EncodeException e) {
					throw new CommandException("Cannot encode a transaction at \"" + publicUri() + "\" in JSON format!", e);
				}
			}
			else
				System.out.println(transaction);
		}
	}

	private String getTransactionRepresentation(RemotePublicNode remote) throws CommandException, TimeoutException, InterruptedException, DatabaseException, NodeException {
		try {
			return remote.getTransactionRepresentation(toBytes(hash)).orElseThrow(() -> new CommandException("The blockchain of the node does not contain any transaction with that hash!"));
		}
		catch (NoSuchAlgorithmException e) {
			throw new CommandException("Unknown cryptographical algorithm in a block of \"" + publicUri() + "\"", e);
		}
		catch (RejectedTransactionException e) {
			throw new CommandException("The transaction exists in blockchain but cannot be transformed into its textual representation", e);
		}
	}

	private Transaction getTransaction(RemotePublicNode remote) throws CommandException, TimeoutException, InterruptedException, DatabaseException, NodeException {
		try {
			return remote.getTransaction(toBytes(hash)).orElseThrow(() -> new CommandException("The blockchain of the node does not contain any transaction with that hash!"));
		}
		catch (NoSuchAlgorithmException e) {
			throw new CommandException("Unknown cryptographical algorithm in a block of \"" + publicUri() + "\"", e);
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