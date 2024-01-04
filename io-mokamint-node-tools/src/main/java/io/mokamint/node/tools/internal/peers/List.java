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
import java.util.concurrent.TimeoutException;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import io.mokamint.node.PeerInfos;
import io.mokamint.node.api.ClosedNodeException;
import io.mokamint.node.api.PeerInfo;
import io.mokamint.node.remote.RemotePublicNodes;
import io.mokamint.node.remote.api.RemotePublicNode;
import io.mokamint.node.tools.internal.AbstractPublicRpcCommand;
import io.mokamint.tools.CommandException;
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

	private void body(RemotePublicNode remote) throws TimeoutException, InterruptedException, ClosedNodeException, CommandException {
		var infos = remote.getPeerInfos().sorted().toArray(PeerInfo[]::new);

		if (json())
			printAsJSON(infos);
		else
			new PrintAsText(infos);
	}

	private class PrintAsText {
		private final PeerInfo[] infos;
		private final int maxPeerLength;
		private final int maxPointsLength;
		private final int maxConnectionLength;
		private final String[] uuids;
		private final String[] versions;
		private final int maxUUIDLength;
		private final int maxVersionLength;

		private PrintAsText(PeerInfo[] infos) {
			this.infos = infos;
			this.maxPeerLength = computeMaxPeerLength();
			this.maxPointsLength = computeMaxPointsLength();
			this.maxConnectionLength = computeMaxConnectionLength();
			this.uuids = new String[infos.length];
			this.versions = new String[infos.length];
			fetchPeersData();
			this.maxUUIDLength = computeMaxUUIDLength();
			this.maxVersionLength = computeMaxVersionLength();
			printTable();
		}

		private void printTable() {
			boolean first = true;
			for (int pos = 0; pos < infos.length; pos++) {
				if (first)
					if (verbose)
						System.out.println(Ansi.AUTO.string("@|green " + formatLine("URI", "points", "status", "UUID", "version") + "|@"));
					else
						System.out.println(Ansi.AUTO.string("@|green " + formatLine("URI", "points", "status") + "|@"));

				System.out.println(formatLine(pos));

				first = false;
			}
		}

		private void fetchPeersData() {
			for (int pos = 0; pos < infos.length; pos++) {
				if (verbose) {
					try (var remote2 = RemotePublicNodes.of(infos[pos].getPeer().getURI(), 10000L)) {
						var peerInfo = remote2.getInfo();
						uuids[pos] = peerInfo.getUUID().toString();
						versions[pos] = peerInfo.getVersion().toString();
					}
					catch (IOException | DeploymentException | TimeoutException | InterruptedException | ClosedNodeException e) {
						LOGGER.log(Level.WARNING, "cannot contact " + infos[pos].getPeer(), e);
						versions[pos] = "<unknown>";
						uuids[pos] = "<unknown>";
					}
				}
				else
					uuids[pos] = versions[pos] = "";
			}
		}

		private String formatLine(int pos) {
			var info = infos[pos];

			if (verbose)
				return formatLine(info.getPeer().toString(), String.valueOf(info.getPoints()), info.isConnected() ? "connected" : "disconnected", uuids[pos], versions[pos]);
			else
				return formatLine(info.getPeer().toString(), String.valueOf(info.getPoints()), info.isConnected() ? "connected" : "disconnected");
		}

		private String formatLine(String peerName, String points, String connected) {
			return String.format("%s   %s  %s", center(peerName, maxPeerLength), center(points, maxPointsLength), center(connected, maxConnectionLength));
		}

		private String formatLine(String peerName, String points, String connected, String uuid, String version) {
			return String.format("%s   %s  %s  %s  %s",
				center(peerName, maxPeerLength),
				center(points, maxPointsLength),
				center(connected, maxConnectionLength),
				center(uuid, maxUUIDLength),
				center(version, maxVersionLength));
		}

		private int computeMaxPeerLength() {
			int maxPeerLength = Stream.concat(Stream.of("URI"), Stream.of(infos).map(PeerInfo::getPeer))
					.map(Object::toString)
					.mapToInt(String::length).max().getAsInt();
		
			return Math.min(maxPeerLength, MAX_PEER_LENGTH);
		}

		private int computeMaxPointsLength() {
			return Stream.concat(Stream.of("points"), Stream.of(infos).map(PeerInfo::getPoints).map(String::valueOf))
					.mapToInt(String::length).max().getAsInt();
		}

		private int computeMaxConnectionLength() {
			return Stream.concat(Stream.of("status"), Stream.of(infos).map(info -> info.isConnected() ? "connected" : "disconnected"))
					.mapToInt(String::length).max().getAsInt();
		}

		private int computeMaxUUIDLength() {
			return Stream.concat(Stream.of("UUID"), Stream.of(uuids))
					.mapToInt(String::length).max().getAsInt();
		}

		private int computeMaxVersionLength() {
			return Stream.concat(Stream.of("version"), Stream.of(versions))
					.mapToInt(String::length).max().getAsInt();
		}
	}

	private void printAsJSON(PeerInfo[] infos) throws CommandException {
		var encoder = new PeerInfos.Encoder();

		try {
			System.out.println(check(EncodeException.class, () -> Stream.of(infos).map(uncheck(encoder::encode)).collect(Collectors.joining(",", "[", "]"))));
		}
		catch (EncodeException e) {
			throw new CommandException("Cannot encode the peers of the node at " + publicUri() + " in JSON format!", e);
		}
	}

	@Override
	protected void execute() throws CommandException {
		execute(this::body);
	}
}