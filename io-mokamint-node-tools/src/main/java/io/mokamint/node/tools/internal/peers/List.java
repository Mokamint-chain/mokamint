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
import java.util.concurrent.TimeoutException;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import io.mokamint.node.Peers;
import io.mokamint.node.api.Peer;
import io.mokamint.node.remote.RemotePublicNodes;
import io.mokamint.tools.AbstractCommand;
import jakarta.websocket.DeploymentException;
import jakarta.websocket.EncodeException;
import picocli.CommandLine.Command;
import picocli.CommandLine.Help.Ansi;
import picocli.CommandLine.Option;

@Command(name = "ls",
	description = "List the peers of a node.",
	showDefaultValues = true
)
public class List extends AbstractCommand {

	@Option(names = "--uri", description = "the network URI where the node is published", defaultValue = "ws://localhost:8025")
	private URI uri;

	@Option(names = "--timeout", description = "the timeout of the connection, in milliseconds", defaultValue = "10000")
	private long timeout;

	@Option(names = "--json", description = "print the output in JSON", defaultValue = "false")
	private boolean json;

	private final static Logger LOGGER = Logger.getLogger(List.class.getName());

	@Override
	protected void execute() {
		try (var remote = RemotePublicNodes.of(uri, timeout)) {
			Stream<Peer> peers = remote.getPeers();
			if (json) {
				var encoder = new Peers.Encoder();
				java.util.List<String> encoded = new ArrayList<>();
				for (var peer: peers.toArray(Peer[]::new))
					encoded.add(encoder.encode(peer));

				System.out.println(encoded.stream().collect(Collectors.joining(",", "[", "]")));
			}
			else
				peers.forEach(System.out::println);
		}
		catch (IOException e) {
			System.out.println(Ansi.AUTO.string("@|red I/O error! Are you sure that a Mokamint node is actually published at " + uri + " and is accessible?|@"));
			LOGGER.log(Level.SEVERE, "I/O error while accessing \"" + uri + "\"", e);
		}
		catch (DeploymentException e) {
			System.out.println(Ansi.AUTO.string("@|red Cannot contact the remote service! Are you sure that a Mokamint node is actually published at " + uri + " and is accessible?|@"));
			LOGGER.log(Level.SEVERE, "failed deployment a remote node for \"" + uri + "\"", e);
		}
		catch (TimeoutException e) {
			System.out.println(Ansi.AUTO.string("@|red Timeout: I waited for " + timeout + "ms but the remote service didn't answer.|@"));
			LOGGER.log(Level.SEVERE, "call timeout to getPeers() on \"" + uri + "\"", e);
		}
		catch (InterruptedException e) {
			System.out.println(Ansi.AUTO.string("@|red Unexpected interruption while waiting for \"" + uri + "\".|@"));
			LOGGER.log(Level.SEVERE, "call to getPeers() on \"" + uri + "\" interrupted", e);
		}
		catch (EncodeException e) {
			System.out.println(Ansi.AUTO.string("@|red Cannot encode in JSON format!|@"));
			LOGGER.log(Level.SEVERE, "cannot encode the peers of the node at \"" + uri + "\" in JSON format.", e);
		}
	}
}