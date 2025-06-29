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

package io.mokamint.node.cli.internal.tasks;

import java.util.concurrent.TimeoutException;

import io.hotmoka.cli.AbstractRow;
import io.hotmoka.cli.AbstractTable;
import io.hotmoka.cli.CommandException;
import io.hotmoka.cli.Table;
import io.mokamint.node.api.NodeException;
import io.mokamint.node.api.TaskInfo;
import io.mokamint.node.cli.internal.AbstractPublicRpcCommand;
import io.mokamint.node.remote.api.RemotePublicNode;
import picocli.CommandLine.Command;
import picocli.CommandLine.Help.Ansi;

@Command(name = "ls", description = "List the tasks of a node.")
public class List extends AbstractPublicRpcCommand {

	private void body(RemotePublicNode remote) throws TimeoutException, InterruptedException, CommandException {
		try {
			new MyTable(remote).print();
		}
		catch (NodeException e) {
			throw new RuntimeException(e); // TODO
		}
	}

	private static class Row extends AbstractRow {
		private final String description;
	
		private Row(String description) {
			this.description = description;
		}

		@Override
		public int numberOfColumns() {
			return 1;
		}

		@Override
		public String getColumn(int index) {
			if (index == 0)
				return description;
			else
				throw new IndexOutOfBoundsException(index);
		}
	
		@Override
		public String toString(int pos, Table table) {
			if (pos == 0)
				return Ansi.AUTO.string("@|green " + String.format("%s", leftAlign(description, table.getSlotsForColumn(0))) + "|@");
			else
				return String.format("%s", leftAlign(description, table.getSlotsForColumn(0)));
		}
	}

	private class MyTable extends AbstractTable {

		private MyTable(RemotePublicNode remote) throws TimeoutException, InterruptedException, NodeException {
			super(new Row("description"), json());
			remote.getTaskInfos().sorted().map(TaskInfo::getDescription).map(Row::new).forEach(this::add);
		}
	}

	@Override
	protected void execute() throws CommandException {
		execute(this::body);
	}
}