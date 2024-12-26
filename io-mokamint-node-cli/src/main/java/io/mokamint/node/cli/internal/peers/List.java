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

import java.io.IOException;
import java.util.concurrent.TimeoutException;
import java.util.logging.Level;
import java.util.logging.Logger;

import io.hotmoka.cli.AbstractRow;
import io.hotmoka.cli.AbstractTable;
import io.hotmoka.cli.CommandException;
import io.hotmoka.cli.Table;
import io.mokamint.node.api.NodeException;
import io.mokamint.node.api.PeerInfo;
import io.mokamint.node.cli.internal.AbstractPublicRpcCommand;
import io.mokamint.node.remote.RemotePublicNodes;
import io.mokamint.node.remote.api.RemotePublicNode;
import jakarta.websocket.DeploymentException;
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

	private void body(RemotePublicNode remote) throws TimeoutException, InterruptedException, NodeException, CommandException {
		new MyTable(remote).print();
	}

	private class Row extends AbstractRow {
		protected final String URI;
		protected final String points;
		protected final String status;
	
		private Row(String URI, String points, String status) {
			this.URI = URI;
			this.points = points;
			this.status = status;
		}

		@Override
		public int numberOfColumns() {
			return 5;
		}

		@Override
		public String getColumn(int index) {
			switch (index) {
			case 0: return URI;
			case 1: return points;
			case 2: return status;
			case 3, 4: return "";
			default: throw new IndexOutOfBoundsException(index);
			}
		}
	
		@Override
		public String toString(int pos, Table table) {
			String result = String.format("%s  %s  %s",
					center(URI, table.getSlotsForColumn(0)),
					rightAlign(points, table.getSlotsForColumn(1)),
					center(status, table.getSlotsForColumn(2)));

			return pos == 0 ? Ansi.AUTO.string("@|green " + result + "|@") : result;
		}
	}

	private class RowVerbose extends Row {
		private final String UUID;
		private final String version;
	
		private RowVerbose(String URI, String points, String status, String UUID, String version) {
			super(URI, points, status);

			this.UUID = UUID;
			this.version = version;
		}

		@Override
		public String getColumn(int index) {
			switch (index) {
			case 3: return UUID;
			case 4: return version;
			default: return super.getColumn(index);
			}
		}
	
		@Override
		public String toString(int pos, Table table) {
			String result = String.format("%s  %s  %s  %s  %s",
				center(URI, table.getSlotsForColumn(0)),
				rightAlign(points, table.getSlotsForColumn(1)),
				center(status, table.getSlotsForColumn(2)),
				center(UUID, table.getSlotsForColumn(3)),
				center(version, table.getSlotsForColumn(4)));

			return pos == 0 ? Ansi.AUTO.string("@|green " + result + "|@") : result;
		}
	}

	private class MyTable extends AbstractTable  {

		private MyTable(RemotePublicNode remote) throws TimeoutException, InterruptedException, NodeException {
			super(verbose ? new RowVerbose("URI", "points", "status", "UUID", "version") : new Row("URI", "points", "status"), json());
			remote.getPeerInfos().sorted().forEach(this::add);
		}

		private void add(PeerInfo info) {
			String URI = info.getPeer().toString();
			if (URI.length() > MAX_PEER_LENGTH)
				URI = URI.substring(0, MAX_PEER_LENGTH) + "...";

			String points = String.valueOf(info.getPoints());
			String status = info.isConnected() ? "connected" : "disconnected";

			if (verbose) {
				String UUID, version;

				try (var remote = RemotePublicNodes.of(info.getPeer().getURI(), 10000)) {
					var peerInfo = remote.getInfo();
					UUID = peerInfo.getUUID().toString();
					version = peerInfo.getVersion().toString();
				}
				catch (NodeException | IOException | DeploymentException | TimeoutException | InterruptedException e) {
					if (e instanceof InterruptedException)
						Thread.currentThread().interrupt();

					LOGGER.log(Level.WARNING, "cannot contact " + info.getPeer() + ": " + e.getMessage());
					UUID = version = "<unknown>";
				}

				add(new RowVerbose(URI, points, status, UUID, version));
			}
			else
				add(new Row(URI, points, status));
		}
	}

	@Override
	protected void execute() throws CommandException {
		execute(this::body);
	}
}