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

package io.mokamint.node.cli.internal.chain;

import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.concurrent.TimeoutException;
import java.util.logging.Logger;

import io.hotmoka.cli.AbstractRow;
import io.hotmoka.cli.AbstractTable;
import io.hotmoka.cli.CommandException;
import io.hotmoka.cli.Table;
import io.hotmoka.crypto.Hex;
import io.mokamint.node.api.DatabaseException;
import io.mokamint.node.api.GenesisBlockDescription;
import io.mokamint.node.api.NodeException;
import io.mokamint.node.api.NonGenesisBlockDescription;
import io.mokamint.node.cli.internal.AbstractPublicRpcCommand;
import io.mokamint.node.remote.api.RemotePublicNode;
import picocli.CommandLine.Command;
import picocli.CommandLine.Help.Ansi;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

@Command(name = "ls", description = "List the blocks in the chain of a node.")
public class List extends AbstractPublicRpcCommand {

	@Parameters(description = "the number of blocks that must be listed", defaultValue = "100")
	private int count;

	@Option(names = "from", description = "the height of the first block that must be reported (-1 to list the topmost count blocks)", defaultValue = "-1")
	private long from;

	private final static Logger LOGGER = Logger.getLogger(List.class.getName());

	/**
	 * The formatter used to print the creation time of the blocks.
	 */
	private final static DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

	private static class Row extends AbstractRow {
		private final String height;
		private final String hash;
		private final String created;
		private final String nodePublicKey;
		private final String minerPublicKey;
	
		private Row(String height, String hash, String created, String nodePublicKey, String minerPublicKey) {
			this.height = height;
			this.hash = hash;
			this.created = created;
			this.nodePublicKey = nodePublicKey;
			this.minerPublicKey = minerPublicKey;
		}
	
		@Override
		public String getColumn(int index) {
			switch (index) {
			case 0: return height;
			case 1: return hash;
			case 2: return created;
			case 3: return nodePublicKey;
			case 4: return minerPublicKey;
			default: throw new IndexOutOfBoundsException(index);
			}
		}
	
		@Override
		public String toString(int pos, Table table) {
			String result = String.format("%s %s  %s  %s  %s",
				rightAlign(height, table.getSlotsForColumn(0)),
				center(hash, table.getSlotsForColumn(1)),
				center(created, table.getSlotsForColumn(2)),
				center(nodePublicKey, table.getSlotsForColumn(3)),
				center(minerPublicKey, table.getSlotsForColumn(4)));
	
			return pos == 0 ? Ansi.AUTO.string("@|green " + result + "|@") : result;
		}
	}

	private class MyTable extends AbstractTable {
		private final RemotePublicNode remote;
		private final byte[][] hashes;
		private final LocalDateTime startDateTimeUTC;

		private MyTable(byte[] genesisHash, RemotePublicNode remote) throws NoSuchAlgorithmException, DatabaseException, TimeoutException, InterruptedException, NodeException {
			super(mkHeader(remote), 5, json());

			this.remote = remote;

			LOGGER.info("requesting hashes in the height interval [" + from + ", " + (from + count) + ")");
			this.hashes = remote.getChainPortion(from, count).getHashes().toArray(byte[][]::new);
			this.startDateTimeUTC = getStartDateTimeUTC(genesisHash);

			for (int pos = hashes.length - 1; pos >= 0; pos--)
				add(pos);
		}

		private static Row mkHeader(RemotePublicNode remote) throws TimeoutException, InterruptedException, NodeException {
			var config = remote.getConfig();
			return new Row("", "block hash (" + config.getHashingForBlocks() + ")",
					"created (UTC)", "node's public key (" + config.getSignatureForBlocks() + ", base58)",
					"miner's public key (" + config.getSignatureForDeadlines() + ", base58)");
		}

		private LocalDateTime getStartDateTimeUTC(byte[] genesisHash) throws DatabaseException, NoSuchAlgorithmException, TimeoutException, InterruptedException, NodeException {
			var maybeGenesis = remote.getBlockDescription(genesisHash);
			if (maybeGenesis.isEmpty())
				throw new DatabaseException("The node has a genesis hash but it is bound to no block!");
			else if (maybeGenesis.get() instanceof GenesisBlockDescription gbd)
				return gbd.getStartDateTimeUTC();
			else
				throw new DatabaseException("The type of the genesis block is inconsistent!");
		}

		private void add(int pos) throws NoSuchAlgorithmException, DatabaseException, TimeoutException, InterruptedException, NodeException {
			long height = from + pos;
			String rowHeight = height + ":";
			String rowHash = Hex.toHexString(hashes[pos]);
			String rowCreated;
			String rowNodePublicKey;
			String rowMinerPublicKey;
	
			var maybeBlockDescription = remote.getBlockDescription(hashes[pos]);
			if (maybeBlockDescription.isEmpty()) {
				if (height == 0L) {
					rowCreated = startDateTimeUTC.format(FORMATTER);
					rowMinerPublicKey = "---";
				}
				else {
					rowCreated = "unknown";
					rowMinerPublicKey = "unknown";
				}
	
				rowNodePublicKey = "unknown";
			}
			else {
				var description = maybeBlockDescription.get();
				rowCreated = startDateTimeUTC.plus(description.getTotalWaitingTime(), ChronoUnit.MILLIS).format(FORMATTER);
				rowNodePublicKey = description.getPublicKeyForSigningBlockBase58();
	
				if (description instanceof NonGenesisBlockDescription ngbd)
					rowMinerPublicKey = ngbd.getDeadline().getProlog().getPublicKeyForSigningDeadlinesBase58();
				else
					rowMinerPublicKey = "---";
			}
	
			add(new Row(rowHeight, rowHash, rowCreated, rowNodePublicKey, rowMinerPublicKey));
		}
	}

	private void body(RemotePublicNode remote) throws CommandException, DatabaseException, TimeoutException, InterruptedException, NodeException {
		if (count < 0)
			throw new CommandException("count cannot be negative!");

		if (from < -1L)
			throw new CommandException("from cannot be smaller than -1!");

		try {
			var info = remote.getChainInfo();
			if (from == -1L)
				from = Math.max(0L, info.getLength() - 1 - count + 1);

			var maybeGenesisHash = info.getGenesisHash();
			if (maybeGenesisHash.isPresent())
				new MyTable(maybeGenesisHash.get(), remote).print();
		}
		catch (NoSuchAlgorithmException e) {
			throw new CommandException("Unknown hashing algorithm in the head of the chain of the node at \"" + publicUri() + "\"!", e);
		}
	}

	@Override
	protected void execute() throws CommandException {
		execute(this::body);
	}
}