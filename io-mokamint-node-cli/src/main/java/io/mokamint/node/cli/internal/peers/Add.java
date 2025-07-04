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

package io.mokamint.node.cli.internal.peers;

import java.net.URI;
import java.util.concurrent.TimeoutException;

import io.hotmoka.cli.CommandException;
import io.mokamint.node.PeerInfos;
import io.mokamint.node.Peers;
import io.mokamint.node.api.ClosedNodeException;
import io.mokamint.node.api.PeerException;
import io.mokamint.node.api.PeerInfo;
import io.mokamint.node.api.PeerRejectedException;
import io.mokamint.node.cli.internal.AbstractRestrictedRpcCommand;
import io.mokamint.node.remote.api.RemoteRestrictedNode;
import jakarta.websocket.EncodeException;
import picocli.CommandLine.Command;
import picocli.CommandLine.Parameters;

@Command(name = "add", description = "Add a peer to a node.")
public class Add extends AbstractRestrictedRpcCommand {

	@Parameters(description = "the URI of the peer to add")
	private URI uri;

	private void body(RemoteRestrictedNode remote) throws TimeoutException, InterruptedException, CommandException, ClosedNodeException {
		PeerInfo info = addPeer(remote);

		if (json()) {
			try {
				System.out.println(new PeerInfos.Encoder().encode(info));
			}
			catch (EncodeException e) {
				throw new CommandException("Cannot encode a peer info of the node at \"" + restrictedUri() + "\" in JSON format!", e);
			}
		}
		else
			System.out.println("Added " + info);
	}

	protected PeerInfo addPeer(RemoteRestrictedNode remote) throws TimeoutException, InterruptedException, ClosedNodeException, CommandException {
		try {
			return remote.add(Peers.of(uri)).orElseThrow(() -> new CommandException("Peer " + uri + " has not been added to the set of peers: was it already present?"));
		}
		catch (PeerException e) {
			throw new CommandException("Cannot establish a connection to " + uri, e);
		}
		catch (PeerRejectedException e) {
			throw new CommandException("The peer at " + uri + " has been rejected", e);
		}
	}

	@Override
	protected void execute() throws CommandException {
		execute(this::body);
	}
}