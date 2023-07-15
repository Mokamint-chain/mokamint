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

import java.io.IOException;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.TimeoutException;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import io.mokamint.node.PeerInfos;
import io.mokamint.node.api.PeerInfo;
import io.mokamint.node.remote.RemotePublicNode;
import io.mokamint.node.remote.RemotePublicNodes;
import io.mokamint.node.tools.internal.AbstractPublicRpcCommand;
import jakarta.websocket.DeploymentException;
import jakarta.websocket.EncodeException;
import picocli.CommandLine.Command;
import picocli.CommandLine.Help.Ansi;
import picocli.CommandLine.Option;

@Command(name = "ls", description = "List the peers of a node.")
public class List extends AbstractPublicRpcCommand {

	@Option(names = "--verbose", description = "report extra information about the peers", defaultValue = "false")
	private boolean verbose;

	private final static Logger LOGGER = Logger.getLogger(List.class.getName());
	
	/**
	 * The maximal length of a peer URI. Beyond that number of characters, the URI string gets truncated.
	 */
	private final static int MAX_PEER_LENGTH = 50;

	private void body(RemotePublicNode remote) throws TimeoutException, InterruptedException {
		try {
			PeerInfo[] infos = remote.getPeerInfos().sorted().toArray(PeerInfo[]::new);
			if (infos.length == 0)
				return;

			if (json()) {
				var encoder = new PeerInfos.Encoder();
				System.out.println(check(EncodeException.class, () ->
					Stream.of(infos).map(uncheck(encoder::encode)).collect(Collectors.joining(",", "[", "]"))
				));
			}
			else {
				int maxLength = Math.min(MAX_PEER_LENGTH, Stream.of(infos).mapToInt(info -> info.getPeer().toString().length()).max().getAsInt());
				if (verbose)
					System.out.println(Ansi.AUTO.string("@|green " + formatLine("URI", maxLength, "points", "status", "UUID", "version") + "|@"));
				else
					System.out.println(Ansi.AUTO.string("@|green " + formatLine("URI", maxLength, "points", "status") + "|@"));

				try (var pool = new ForkJoinPool()) {
					pool.execute(() ->
						Stream.of(infos).parallel().map(info -> formatLine(info, maxLength)).forEachOrdered(System.out::println)
					);
				}
			}
		}
		catch (EncodeException e) {
			System.out.println(Ansi.AUTO.string("@|red Cannot encode in JSON format!|@"));
			LOGGER.log(Level.SEVERE, "cannot encode the peers of the node at \"" + publicUri() + "\" in JSON format.", e);
		}
	}

	private String formatLine(PeerInfo info, int maxLength) {
		if (verbose) {
			String version = "<unknown>";
			String uuid = "<unknown>";

			try (var remote = RemotePublicNodes.of(info.getPeer().getURI(), 10000)) {
				var peerInfo = remote.getInfo();
				version = peerInfo.getVersion().toString();
				uuid = peerInfo.getUUID().toString();
			}
			catch (IOException | DeploymentException | TimeoutException | InterruptedException e) {
				LOGGER.log(Level.WARNING, "cannot contact " + info.getPeer(), e);
			}

			return formatLine(info.getPeer().toString(), maxLength, String.valueOf(info.getPoints()), info.isConnected() ? "connected" : "disconnected", uuid, version);
		}
		else
			return formatLine(info.getPeer().toString(), maxLength, String.valueOf(info.getPoints()), info.isConnected() ? "connected" : "disconnected");
	}

	private String formatLine(String peer, int peerNameSize, String points, String connected) {
		return String.format("%-" + peerNameSize + "s   %6s  %-12s", peer, points, connected);
	}

	private String formatLine(String peer, int peerNameSize, String points, String connected, String uuid, String version) {
		return String.format("%-" + peerNameSize + "s   %6s  %-12s  %-36s  %s", peer, points, connected, uuid, version);
	}

	@Override
	protected void execute() {
		execute(this::body, LOGGER);
	}
}