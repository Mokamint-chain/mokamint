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

package io.mokamint.node.tools.internal.peers;

import java.io.IOException;
import java.net.URI;
import java.util.concurrent.TimeoutException;

import io.mokamint.cli.CommandException;
import io.mokamint.node.Peers;
import io.mokamint.node.api.DatabaseException;
import io.mokamint.node.api.NodeException;
import io.mokamint.node.remote.api.RemoteRestrictedNode;
import io.mokamint.node.tools.internal.AbstractRestrictedRpcCommand;
import picocli.CommandLine.Command;
import picocli.CommandLine.Parameters;

@Command(name = "rm", description = "Remove a peer from a node.")
public class Remove extends AbstractRestrictedRpcCommand {

	@Parameters(description = "the URI of the peer to remove")
	private URI uri;

	private void body(RemoteRestrictedNode remote) throws NodeException, TimeoutException, InterruptedException, CommandException, DatabaseException {
		if (removePeer(remote))
			if (json())
				System.out.println(uri);
			else
				System.out.println("Removed peer " + uri);
		else
			throw new CommandException("Peer " + uri + " has not been removed from the set of peers: are you sure that it exists?");
	}

	private boolean removePeer(RemoteRestrictedNode remote) throws CommandException, TimeoutException, InterruptedException, NodeException, DatabaseException {
		try {
			return remote.remove(Peers.of(uri));
		}
		catch (IOException e) {
			throw new CommandException("Cannot establish a connection to " + uri, e);
		}
	}

	@Override
	protected void execute() throws CommandException {
		execute(this::body);
	}
}