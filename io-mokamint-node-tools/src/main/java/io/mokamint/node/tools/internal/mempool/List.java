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
import java.util.stream.IntStream;
import java.util.stream.Stream;

import io.hotmoka.crypto.Hex;
import io.mokamint.node.MempoolPortions;
import io.mokamint.node.api.ConsensusConfig;
import io.mokamint.node.api.MempoolEntry;
import io.mokamint.node.api.MempoolPortion;
import io.mokamint.node.api.NodeException;
import io.mokamint.node.remote.api.RemotePublicNode;
import io.mokamint.node.tools.internal.AbstractPublicRpcCommand;
import io.mokamint.tools.CommandException;
import jakarta.websocket.EncodeException;
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

	private void body(RemotePublicNode remote) throws TimeoutException, InterruptedException, NodeException, CommandException {
		if (count < 0)
			throw new CommandException("count cannot be negative!");

		if (from < -1)
			throw new CommandException("from cannot be smaller than -1!");

		if (from == -1L)
			from = (int) Math.max(0, remote.getMempoolInfo().getSize() - count);

		LOGGER.info("requesting mempool entries with index in the interval [" + from + ", " + (from + count) + ")");
		var mempool = remote.getMempoolPortion(from, count);

		if (json()) {
			try {
				System.out.println(new MempoolPortions.Encoder().encode(mempool));
			}
			catch (EncodeException e) {
				throw new CommandException("Cannot encode a mempool portion of the node at \"" + publicUri() + "\" in JSON format!", e);
			}
		}
		else if (mempool.getEntries().count() != 0)
			new ListMempool(mempool, remote);
	}

	private class ListMempool {
		private final ConsensusConfig<?, ?> config;
		private final MempoolEntry[] entries;
		private final String[] heights;
		private final int slotsForHeight;
		private final String[] hashes;
		private final int slotsForHash;
		private final String[] priorities;
		private final int slotsForPriority;

		/**
		 * Lists the entries in {@code mempool}.
		 * 
		 * @param mempool the mempool portion to list
		 * @param the remote node
		 * @throws TimeoutException if some connection timed-out
		 * @throws InterruptedException if some connection was interrupted while waiting
		 * @throws NodeException if the remote node could not complete the operation
		 */
		private ListMempool(MempoolPortion mempool, RemotePublicNode remote) throws TimeoutException, InterruptedException, NodeException {
			this.config = remote.getConfig();
			this.entries = mempool.getEntries().toArray(MempoolEntry[]::new);
			this.heights = new String[1 + entries.length];
			this.hashes = new String[heights.length];
			this.priorities = new String[heights.length];
			fillColumns();
			this.slotsForHeight = Stream.of(heights).mapToInt(String::length).max().getAsInt();
			this.slotsForHash = Stream.of(hashes).mapToInt(String::length).max().getAsInt();
			this.slotsForPriority = Stream.of(priorities).mapToInt(String::length).max().getAsInt();
			printRows();
		}

		private void fillColumns() {
			heights[0] = "";
			hashes[0] = "tx hash (" + config.getHashingForTransactions() + ")";
			priorities[0] = "priority";
			
			for (int pos = 1; pos < hashes.length; pos++) {
				heights[pos] = (from + entries.length - pos) + ":";
				hashes[pos] = Hex.toHexString(entries[entries.length - pos].getHash());
				priorities[pos] = String.valueOf(entries[entries.length - pos].getPriority());
			}
		}

		private void printRows() {
			IntStream.iterate(0, i -> i + 1).limit(heights.length).mapToObj(this::format).forEach(System.out::println);
		}

		private String format(int pos) {
			String result = String.format("%s %s  %s",
				rightAlign(heights[pos], slotsForHeight),
				center(hashes[pos], slotsForHash),
				rightAlign(priorities[pos], slotsForPriority));

			return pos == 0 ? Ansi.AUTO.string("@|green " + result + "|@") : result;
		}
	}

	@Override
	protected void execute() throws CommandException {
		execute(this::body);
	}
}