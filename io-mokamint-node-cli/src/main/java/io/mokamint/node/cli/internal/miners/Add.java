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

package io.mokamint.node.cli.internal.miners;

import java.io.IOException;
import java.util.concurrent.TimeoutException;

import io.hotmoka.cli.CommandException;
import io.mokamint.node.DatabaseException;
import io.mokamint.node.MinerInfos;
import io.mokamint.node.api.MinerInfo;
import io.mokamint.node.api.NodeException;
import io.mokamint.node.cli.internal.AbstractRestrictedRpcCommand;
import io.mokamint.node.remote.api.RemoteRestrictedNode;
import jakarta.websocket.EncodeException;
import picocli.CommandLine.Command;
import picocli.CommandLine.Parameters;

@Command(name = "add", description = "Add a remote miner to a node.")
public class Add extends AbstractRestrictedRpcCommand {

	@Parameters(description = "the port where the miner must be published")
	private int port;

	private void body(RemoteRestrictedNode remote) throws NodeException, TimeoutException, InterruptedException, CommandException, DatabaseException {
		if (port < 0 || port > 65535)
			throw new CommandException("The port number must be between 0 and 65535 inclusive");

		MinerInfo info = openMiner(remote);

		if (json()) {
			try {
				System.out.println(new MinerInfos.Encoder().encode(info));
			}
			catch (EncodeException e) {
				throw new CommandException("Cannot encode a miner info of the node at \"" + restrictedUri() + "\" in JSON format!", e);
			}
		}
		else
			System.out.println("Opened " + info);
	}

	protected MinerInfo openMiner(RemoteRestrictedNode remote) throws TimeoutException, InterruptedException, NodeException, CommandException {
		try {
			return remote.openMiner(port).orElseThrow(() -> new CommandException("No remote miner has been opened"));
		}
		catch (IOException e) {
			throw new CommandException("Cannot open a remote miner at port " + port + "! Are you sure that the port is available?", e);
		}
	}

	@Override
	protected void execute() throws CommandException {
		execute(this::body);
	}
}