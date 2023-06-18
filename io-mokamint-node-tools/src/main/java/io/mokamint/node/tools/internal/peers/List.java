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

import java.util.ArrayList;
import java.util.concurrent.TimeoutException;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import io.mokamint.node.Peers;
import io.mokamint.node.api.Peer;
import io.mokamint.node.remote.RemotePublicNode;
import io.mokamint.node.tools.internal.AbstractPublicRpcCommand;
import jakarta.websocket.EncodeException;
import picocli.CommandLine.Command;
import picocli.CommandLine.Help.Ansi;

@Command(name = "ls", description = "List the peers of a node.")
public class List extends AbstractPublicRpcCommand {

	private final static Logger LOGGER = Logger.getLogger(List.class.getName());

	private void body(RemotePublicNode remote) throws TimeoutException, InterruptedException {
		try {
			Stream<Peer> peers = remote.getPeers();
			if (json()) {
				var encoder = new Peers.Encoder();
				java.util.List<String> encoded = new ArrayList<>();
				for (var peer: peers.toArray(Peer[]::new))
					encoded.add(encoder.encode(peer));

				System.out.println(encoded.stream().collect(Collectors.joining(",", "[", "]")));
			}
			else
				peers.forEach(System.out::println);
		}
		catch (EncodeException e) {
			System.out.println(Ansi.AUTO.string("@|red Cannot encode in JSON format!|@"));
			LOGGER.log(Level.SEVERE, "cannot encode the peers of the node at \"" + publicUri() + "\" in JSON format.", e);
		}
	}

	@Override
	protected void execute() {
		execute(this::body, LOGGER);
	}
}