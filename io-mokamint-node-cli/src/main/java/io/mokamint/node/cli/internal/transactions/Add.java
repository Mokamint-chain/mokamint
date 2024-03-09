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

package io.mokamint.node.cli.internal.transactions;

import java.security.NoSuchAlgorithmException;
import java.util.concurrent.TimeoutException;

import io.hotmoka.cli.CommandException;
import io.hotmoka.crypto.Base64;
import io.hotmoka.crypto.Base64ConversionException;
import io.mokamint.node.MempoolEntries;
import io.mokamint.node.Transactions;
import io.mokamint.node.api.DatabaseException;
import io.mokamint.node.api.MempoolEntry;
import io.mokamint.node.api.NodeException;
import io.mokamint.node.api.RejectedTransactionException;
import io.mokamint.node.cli.internal.AbstractPublicRpcCommand;
import io.mokamint.node.remote.api.RemotePublicNode;
import jakarta.websocket.EncodeException;
import picocli.CommandLine.Command;
import picocli.CommandLine.Parameters;

@Command(name = "add", description = "Add a transaction to the mempool of a node.")
public class Add extends AbstractPublicRpcCommand {

	@Parameters(description = "the Base64-encoded bytes of the transaction to add")
	private String tx;

	private void body(RemotePublicNode remote) throws NodeException, DatabaseException, TimeoutException, InterruptedException, CommandException {
		MempoolEntry info = addTransaction(remote);

		if (json()) {
			try {
				System.out.println(new MempoolEntries.Encoder().encode(info));
			}
			catch (EncodeException e) {
				throw new CommandException("Cannot encode a mempool entry at \"" + publicUri() + "\" in JSON format!", e);
			}
		}
		else
			System.out.println(info);
	}

	private MempoolEntry addTransaction(RemotePublicNode remote) throws TimeoutException, InterruptedException, DatabaseException, NodeException, CommandException {
		try {
			return remote.add(Transactions.of(Base64.fromBase64String(tx)));
		}
		catch (Base64ConversionException e) {
			throw new CommandException("Illegal Base64 encoding of the transaction!", e);
		}
		catch (NoSuchAlgorithmException e) {
			throw new CommandException("The database of the node contains a block that refers to an unknown cryptographic algorithm: " + e.getMessage(), e);
		}
		catch (RejectedTransactionException e) {
			throw new CommandException("The transaction has been rejected: " + e.getMessage(), e);
		}
	}

	@Override
	protected void execute() throws CommandException {
		execute(this::body);
	}
}