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

package io.mokamint.node.cli.internal.config;

import java.util.concurrent.TimeoutException;

import io.hotmoka.cli.CommandException;
import io.mokamint.node.BasicConsensusConfigBuilders;
import io.mokamint.node.api.NodeException;
import io.mokamint.node.cli.internal.AbstractPublicRpcCommand;
import io.mokamint.node.remote.api.RemotePublicNode;
import jakarta.websocket.EncodeException;
import picocli.CommandLine.Command;

@Command(name = "show", description = "Show the configuration of a node.")
public class Show extends AbstractPublicRpcCommand {

	private void body(RemotePublicNode remote) throws TimeoutException, InterruptedException, CommandException {
		try {
			var config = remote.getConfig();

			if (json())
				System.out.println(new BasicConsensusConfigBuilders.Encoder().encode(config));
			else
				System.out.println(config);
		}
		catch (EncodeException e) {
			throw new CommandException("Cannot encode the configuration of the node at \"" + publicUri() + "\" in JSON format.", e);
		}
		catch (NodeException e) {
			throw new RuntimeException(e); // TODO
		}
	}

	@Override
	protected void execute() throws CommandException {
		execute(this::body);
	}
}