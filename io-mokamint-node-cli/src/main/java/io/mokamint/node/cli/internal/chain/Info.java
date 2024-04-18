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

import java.util.concurrent.TimeoutException;

import io.hotmoka.cli.CommandException;
import io.mokamint.node.ChainInfos;
import io.mokamint.node.api.NodeException;
import io.mokamint.node.cli.internal.AbstractPublicRpcCommand;
import io.mokamint.node.remote.api.RemotePublicNode;
import jakarta.websocket.EncodeException;
import picocli.CommandLine.Command;

@Command(name = "info", description = "Show information about the chain of a node.")
public class Info extends AbstractPublicRpcCommand {

	private void body(RemotePublicNode remote) throws TimeoutException, InterruptedException, NodeException, CommandException {
		try {
			var info = remote.getChainInfo();

			if (json())
				System.out.println(new ChainInfos.Encoder().encode(info));
			else
				System.out.println(info);
		}
		catch (EncodeException e) {
			throw new CommandException("Cannot encode the chain info of the node at \"" + publicUri() + "\" in JSON format!", e);
		}
	}

	@Override
	protected void execute() throws CommandException {
		execute(this::body);
	}
}