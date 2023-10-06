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
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeoutException;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import io.mokamint.node.Peers;
import io.mokamint.node.api.ClosedNodeException;
import io.mokamint.node.api.DatabaseException;
import io.mokamint.node.api.Peer;
import io.mokamint.node.api.PeerRejectedException;
import io.mokamint.node.remote.api.RemoteRestrictedNode;
import io.mokamint.node.tools.internal.AbstractRestrictedRpcCommand;
import io.mokamint.tools.CommandException;
import jakarta.websocket.EncodeException;
import picocli.CommandLine.Command;
import picocli.CommandLine.Parameters;

@Command(name = "add", description = "Add peers to a node.")
public class Add extends AbstractRestrictedRpcCommand {

	@Parameters(description = "the URIs of the peers to add")
	private URI[] uris;

	private class Run {
		private final RemoteRestrictedNode remote;
		private final List<String> successes = new ArrayList<>();

		private Run(RemoteRestrictedNode remote) throws ClosedNodeException, TimeoutException, InterruptedException {
			this.remote = remote;

			Optional<Exception> exception = Stream.of(uris)
				.parallel()
				.map(Peers::of)
				.map(this::addPeer)
				.flatMap(Optional::stream)
				.findFirst();

			if (json())
				System.out.println(successes.stream().collect(Collectors.joining(", ", "[", "]")));

			if (exception.isPresent())
				throwAsRpcCommandException(exception.get());
		}

		private Optional<Exception> addPeer(Peer peer) {
			try {
				if (remote.add(peer))
					if (json())
						successes.add(new Peers.Encoder().encode(peer));
					else
						System.out.println("Added " + peer + " to the set of peers");
				else
					System.out.println("Peer " + peer + " has not been added to the set of peers");

				return Optional.empty();
			}
			catch (RuntimeException | ClosedNodeException | TimeoutException | InterruptedException | DatabaseException e) {
				return Optional.of(e);
			}
			catch (PeerRejectedException e) {
				return Optional.of(new CommandException(e.getMessage(), e));
			}
			catch (EncodeException e) {
				return Optional.of(new CommandException("Cannot encode " + peer + " in JSON", e));
			}
			catch (IOException e) {
				return Optional.of(new CommandException("Cannot establish a connection to " + peer, e));
			}
		}
	}

	@Override
	protected void execute() throws CommandException {
		if (uris == null)
			uris = new URI[0];

		execute(Run::new);
	}
}