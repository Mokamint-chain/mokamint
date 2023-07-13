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
import java.util.logging.Logger;
import java.util.stream.Stream;

import io.mokamint.node.Peers;
import io.mokamint.node.api.DatabaseException;
import io.mokamint.node.api.IncompatiblePeerException;
import io.mokamint.node.api.Peer;
import io.mokamint.node.remote.RemoteRestrictedNode;
import io.mokamint.node.tools.internal.AbstractRestrictedRpcCommand;
import jakarta.websocket.EncodeException;
import picocli.CommandLine.Command;
import picocli.CommandLine.Help.Ansi;
import picocli.CommandLine.Parameters;

@Command(name = "add", description = "Add peers to a node.")
public class Add extends AbstractRestrictedRpcCommand {

	@Parameters(description = { "the URIs of the peers to add" })
	private URI[] uris;

	private final static Logger LOGGER = Logger.getLogger(Add.class.getName());

	private void body(RemoteRestrictedNode remote) throws TimeoutException, InterruptedException {
		Stream.of(uris).map(Peers::of).parallel().forEach(peer -> addPeer(peer, remote));
	}

	private void addPeer(Peer peer, RemoteRestrictedNode remote) {
		try {
			remote.addPeer(peer);
			if (json())
				System.out.println(new Peers.Encoder().encode(peer));
			else
				System.out.println("Added " + peer + " to the set of peers");
		}
		catch (TimeoutException e) {
			System.out.println(Ansi.AUTO.string("@|red Connection timeout while adding peer " + peer + "!|@"));
		}
		catch (InterruptedException e) {
			System.out.println(Ansi.AUTO.string("@|red Process interrupted while waiting for the addition of peer " + peer + "!|@"));
		}
		catch (IncompatiblePeerException e) {
			System.out.println(Ansi.AUTO.string("@|red " + capitalize(e.getMessage()) + "!|@"));
		}
		catch (DatabaseException e) {
			System.out.println(Ansi.AUTO.string("@|red The database of the node seems corrupted!|@"));
		}
		catch (EncodeException e) {
			System.out.println(Ansi.AUTO.string("@|red Cannot encode " + peer + " in JSON!|@"));
		}
		catch (IOException e) {
			System.out.println(Ansi.AUTO.string("@|red Cannot establish a connection to " + peer + "!|@"));
		}
	}

	private static String capitalize(String message) {
		if (message.length() > 0)
			return Character.toUpperCase(message.charAt(0)) + message.substring(1);
		else
			return message;
	}

	@Override
	protected void execute() {
		if (uris == null)
			uris = new URI[0];

		execute(this::body, LOGGER);
	}
}