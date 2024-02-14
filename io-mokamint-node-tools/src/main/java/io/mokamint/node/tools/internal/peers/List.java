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
import java.util.stream.IntStream;
import java.util.stream.Stream;

import io.mokamint.node.PeerInfos;
import io.mokamint.node.api.ClosedNodeException;
import io.mokamint.node.api.NodeException;
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
		else if (infos.length > 0)
			new PrintAsText(infos);
	}

	private class PrintAsText {
		private final String[] URIs;
		private final int slotsForURI;
		private final String[] points;
		private final int slotsForPoints;
		private final String[] statuses;
		private final int slotsForStatus;
		private final String[] UUIDs;
		private final int slotsForUUID;
		private final String[] versions;
		private final int slotsForVersion;

		private PrintAsText(PeerInfo[] infos) {
			this.URIs = new String[1 + infos.length];
			this.points = new String[URIs.length];
			this.statuses = new String[URIs.length];
			this.UUIDs = new String[URIs.length];
			this.versions = new String[URIs.length];
			fillColumns(infos);
			this.slotsForURI = Stream.of(URIs).mapToInt(String::length).max().getAsInt();
			this.slotsForPoints = Stream.of(points).mapToInt(String::length).max().getAsInt();
			this.slotsForStatus = Stream.of(statuses).mapToInt(String::length).max().getAsInt();
			this.slotsForUUID = Stream.of(UUIDs).mapToInt(String::length).max().getAsInt();
			this.slotsForVersion = Stream.of(versions).mapToInt(String::length).max().getAsInt();
			printRows();
		}

		private void fillColumns(PeerInfo[] infos) {
			URIs[0] = "URI";
			points[0] = "points";
			statuses[0] = "status";
			UUIDs[0] = "UUID";
			versions[0] = "version";
			
			for (int pos = 1; pos < URIs.length; pos++) {
				PeerInfo info = infos[pos - 1];
				URIs[pos] = info.getPeer().toString();
				if (URIs[pos].length() > MAX_PEER_LENGTH)
					URIs[pos] = URIs[pos].substring(0, MAX_PEER_LENGTH) + "...";

				points[pos] = String.valueOf(info.getPoints());
				statuses[pos] = info.isConnected() ? "connected" : "disconnected";

				if (verbose) {
					try (var remote = RemotePublicNodes.of(info.getPeer().getURI(), 10000L)) {
						var peerInfo = remote.getInfo();
						UUIDs[pos] = peerInfo.getUUID().toString();
						versions[pos] = peerInfo.getVersion().toString();
					}
					catch (NodeException | IOException | DeploymentException | TimeoutException | InterruptedException | ClosedNodeException e) {
						if (e instanceof InterruptedException)
							Thread.currentThread().interrupt();

						LOGGER.log(Level.WARNING, "cannot contact " + info.getPeer(), e);
						UUIDs[pos] = "<unknown>";
						versions[pos] = "<unknown>";
					}
				}
				else
					UUIDs[pos] = versions[pos] = "";
			}
		}

		private void printRows() {
			IntStream.iterate(0, i -> i + 1).limit(URIs.length).mapToObj(this::format).forEach(System.out::println);
		}

		private String format(int pos) {
			String result;
			if (verbose)
				result = String.format("%s  %s  %s  %s  %s",
						center(URIs[pos], slotsForURI),
						rightAlign(points[pos], slotsForPoints),
						center(statuses[pos], slotsForStatus),
						center(UUIDs[pos], slotsForUUID),
						center(versions[pos], slotsForVersion));
			else
				result = String.format("%s  %s  %s",
						center(URIs[pos], slotsForURI),
						rightAlign(points[pos], slotsForPoints),
						center(statuses[pos], slotsForStatus));

			return pos == 0 ? Ansi.AUTO.string("@|green " + result + "|@") : result;
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