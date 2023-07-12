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

import static io.hotmoka.exceptions.CheckSupplier.check;
import static io.hotmoka.exceptions.UncheckFunction.uncheck;

import java.util.OptionalInt;
import java.util.concurrent.TimeoutException;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import io.mokamint.node.PeerInfos;
import io.mokamint.node.api.PeerInfo;
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
			PeerInfo[] infos = remote.getPeers().sorted().toArray(PeerInfo[]::new);
			if (json()) {
				var encoder = new PeerInfos.Encoder();
				System.out.println(check(EncodeException.class, () ->
					Stream.of(infos).map(uncheck(encoder::encode)).collect(Collectors.joining(",", "[", "]"))
				));
			}
			else {
				OptionalInt maxLength = Stream.of(infos).mapToInt(info -> info.getPeer().toString().length()).max();
				int size = maxLength.isEmpty() || maxLength.getAsInt() > 50 ? 50: maxLength.getAsInt();
				printLine("URI", size, "points", "status", true);
				Stream.of(infos).forEachOrdered(info -> printLine(info.getPeer().toString(), size, String.valueOf(info.getPoints()), info.isConnected() ? "connected" : "disconnected", false));
			}
		}
		catch (EncodeException e) {
			System.out.println(Ansi.AUTO.string("@|red Cannot encode in JSON format!|@"));
			LOGGER.log(Level.SEVERE, "cannot encode the peers of the node at \"" + publicUri() + "\" in JSON format.", e);
		}
	}

	private void printLine(String peer, int peerNameSize, String points, String connected, boolean color) {
		String line = String.format("%-" + peerNameSize + "s   %6s  %s", peer, points, connected);
		if (color)
			line = Ansi.AUTO.string("@|red " + line + "|@");

		System.out.println(line);
	}

	@Override
	protected void execute() {
		execute(this::body, LOGGER);
	}
}