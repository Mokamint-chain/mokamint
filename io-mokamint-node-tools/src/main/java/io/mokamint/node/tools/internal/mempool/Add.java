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

package io.mokamint.node.tools.internal.mempool;

import java.util.concurrent.TimeoutException;

import io.hotmoka.crypto.Base64;
import io.hotmoka.crypto.Base64ConversionException;
import io.hotmoka.websockets.beans.EncodeException;
import io.mokamint.node.TransactionInfos;
import io.mokamint.node.Transactions;
import io.mokamint.node.api.ClosedNodeException;
import io.mokamint.node.api.RejectedTransactionException;
import io.mokamint.node.api.TransactionInfo;
import io.mokamint.node.remote.api.RemotePublicNode;
import io.mokamint.node.tools.internal.AbstractPublicRpcCommand;
import io.mokamint.tools.CommandException;
import picocli.CommandLine.Command;
import picocli.CommandLine.Parameters;

@Command(name = "add", description = "Add a transaction to the mempool of a node.")
public class Add extends AbstractPublicRpcCommand {

	@Parameters(description = "the transaction to add, in Base64 format")
	private String tx;

	private void body(RemotePublicNode remote) throws ClosedNodeException, TimeoutException, InterruptedException, CommandException {
		TransactionInfo info;

		try {
			info = remote.add(Transactions.of(Base64.fromBase64String(tx)));
		}
		catch (Base64ConversionException e) {
			throw new CommandException("Illegal Base64 encoding of the transaction!", e);
		}
		catch (RejectedTransactionException e) {
			throw new CommandException("The transaction has been rejected: " + e.getMessage(), e);
		}

		if (json()) {
			try {
				System.out.println(new TransactionInfos.Encoder().encode(info));
			}
			catch (EncodeException e) {
				throw new CommandException("Cannot encode " + info + " in JSON!", e);
			}
		}
		else
			System.out.println(info);
	}

	@Override
	protected void execute() throws CommandException {
		execute(this::body);
	}
}