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

package io.mokamint.node.cli.internal.miners;

import java.util.concurrent.TimeoutException;

import io.hotmoka.cli.AbstractRow;
import io.hotmoka.cli.AbstractTable;
import io.hotmoka.cli.CommandException;
import io.hotmoka.cli.Table;
import io.mokamint.node.api.MinerInfo;
import io.mokamint.node.api.NodeException;
import io.mokamint.node.cli.internal.AbstractPublicRpcCommand;
import io.mokamint.node.remote.api.RemotePublicNode;
import picocli.CommandLine.Command;
import picocli.CommandLine.Help.Ansi;

@Command(name = "ls", description = "List the miners of a node.")
public class List extends AbstractPublicRpcCommand {

	private void body(RemotePublicNode remote) throws TimeoutException, InterruptedException, NodeException {
		new MyTable(remote).print();
	}

	private static class Row extends AbstractRow {
		private final String UUID;
		private final String points;
		private final String description;
	
		private Row(String UUID, String points, String description) {
			this.UUID = UUID;
			this.points = points;
			this.description = description;
		}
	
		@Override
		public String getColumn(int index) {
			switch (index) {
			case 0: return UUID;
			case 1: return points;
			case 2: return description;
			default: throw new IndexOutOfBoundsException(index);
			}
		}
	
		@Override
		public String toString(int pos, Table table) {
			if (pos == 0)
				return Ansi.AUTO.string("@|green " + String.format("%s %s   %s",
					center(UUID, table.getSlotsForColumn(0)),
					center(points, table.getSlotsForColumn(1)),
					center(description, table.getSlotsForColumn(2))) + "|@");
			else
				return String.format("%s %s   %s",
					center(UUID, table.getSlotsForColumn(0)),
					rightAlign(points, table.getSlotsForColumn(1)),
					leftAlign(description, table.getSlotsForColumn(2)));
		}
	}

	private class MyTable extends AbstractTable {

		private MyTable(RemotePublicNode remote) throws TimeoutException, InterruptedException, NodeException {
			super(new Row("UUID", "points", "description"), 3, json());
			remote.getMinerInfos().sorted().forEach(this::add);
		}

		private void add(MinerInfo info) {
			String UUID = String.valueOf(info.getUUID());
			String points = String.valueOf(info.getPoints());
			String description = info.getDescription();

			add(new Row(UUID, points, description));
		}
	}

	@Override
	protected void execute() throws CommandException {
		execute(this::body);
	}
}