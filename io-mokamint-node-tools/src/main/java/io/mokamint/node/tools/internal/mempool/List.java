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

package io.mokamint.node.tools.internal.mempool;

import java.util.concurrent.TimeoutException;
import java.util.logging.Logger;

import io.hotmoka.crypto.Hex;
import io.mokamint.node.api.MempoolEntry;
import io.mokamint.node.api.NodeException;
import io.mokamint.node.remote.api.RemotePublicNode;
import io.mokamint.node.tools.internal.AbstractPublicRpcCommand;
import io.mokamint.tools.AbstractRow;
import io.mokamint.tools.AbstractTable;
import io.mokamint.tools.CommandException;
import io.mokamint.tools.Table;
import picocli.CommandLine.Command;
import picocli.CommandLine.Help.Ansi;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

@Command(name = "ls", description = "List the transactions in the mempool of a node.")
public class List extends AbstractPublicRpcCommand {

	@Parameters(description = "the number of transactions that must be listed", defaultValue = "100")
	private int count;

	@Option(names = "from", description = "the index of the first transaction that must be reported (-1 to list the highest-priority count transactions)", defaultValue = "-1")
	private int from;

	private final static Logger LOGGER = Logger.getLogger(List.class.getName());

	private void body(RemotePublicNode remote) throws CommandException, TimeoutException, InterruptedException, NodeException{
		if (count < 0)
			throw new CommandException("count cannot be negative!");

		if (from < -1)
			throw new CommandException("from cannot be smaller than -1!");

		if (from == -1L)
			from = (int) Math.max(0, remote.getMempoolInfo().getSize() - count);

		new MyTable(remote).print();
	}

	private static class Row extends AbstractRow {
		private final String height;
		private final String hash;
		private final String priority;
	
		private Row(String height, String hash, String priority) {
			this.height = height;
			this.hash = hash;
			this.priority = priority;
		}
	
		@Override
		public String getColumn(int index) {
			switch (index) {
			case 0: return height;
			case 1: return hash;
			case 2: return priority;
			default: throw new IndexOutOfBoundsException(index);
			}
		}
	
		@Override
		public String toString(int pos, Table table) {
			String result = String.format("%s %s  %s",
				rightAlign(height, table.getSlotsForColumn(0)),
				center(hash, table.getSlotsForColumn(1)),
				rightAlign(priority, table.getSlotsForColumn(2)));

			return pos == 0 ? Ansi.AUTO.string("@|green " + result + "|@") : result;
		}
	}

	private class MyTable extends AbstractTable {
		private final MempoolEntry[] entries;

		private MyTable(RemotePublicNode remote) throws TimeoutException, InterruptedException, NodeException {
			super(mkHeader(remote), 3, json());

			LOGGER.info("requesting mempool entries with index in the interval [" + from + ", " + (from + count) + ")");
			this.entries = remote.getMempoolPortion(from, count).getEntries().toArray(MempoolEntry[]::new);

			for (int pos = entries.length - 1; pos >= 0; pos--)
				add(pos);
		}

		private static Row mkHeader(RemotePublicNode remote) throws TimeoutException, InterruptedException, NodeException {
			var config = remote.getConfig();
			return new Row("", "tx hash (" + config.getHashingForTransactions() + ")", "priority");
		}

		private void add(int pos) {
			String height = (from + pos) + ":";
			String hash = Hex.toHexString(entries[pos].getHash());
			String priority = String.valueOf(entries[pos].getPriority());

			add(new Row(height, hash, priority));
		}
	}

	@Override
	protected void execute() throws CommandException {
		execute(this::body);
	}
}