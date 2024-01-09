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

package io.mokamint.node.tools.internal.chain;

import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.concurrent.TimeoutException;
import java.util.logging.Logger;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import com.google.gson.Gson;

import io.hotmoka.crypto.Hex;
import io.mokamint.node.api.ChainPortion;
import io.mokamint.node.api.ClosedNodeException;
import io.mokamint.node.api.ConsensusConfig;
import io.mokamint.node.api.DatabaseException;
import io.mokamint.node.api.GenesisBlockDescription;
import io.mokamint.node.api.NonGenesisBlockDescription;
import io.mokamint.node.remote.api.RemotePublicNode;
import io.mokamint.node.tools.internal.AbstractPublicRpcCommand;
import io.mokamint.tools.CommandException;
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

	private void body(RemotePublicNode remote) throws CommandException, DatabaseException, TimeoutException, InterruptedException, ClosedNodeException {
		if (count < 0)
			throw new CommandException("count cannot be negative!");

		if (from < -1L)
			throw new CommandException("from cannot be smaller than -1!");

		try {
			var info = remote.getChainInfo();
			if (from == -1L)
				from = Math.max(0L, info.getLength() - 1 - count + 1);

			LOGGER.info("requesting hashes in the height interval [" + from + ", " + (from + count) + ")");
			ChainPortion chain = remote.getChainPortion(from, count);

			if (json())
				System.out.println(new Gson().toJsonTree(chain.getHashes().map(Hex::toHexString).toArray(String[]::new)));
			else if (chain.getHashes().count() != 0) {
				var maybeGenesisHash = info.getGenesisHash();
				if (maybeGenesisHash.isPresent()) {
					var maybeGenesis = remote.getBlockDescription(maybeGenesisHash.get());
					if (maybeGenesis.isEmpty())
						throw new DatabaseException("The node has a genesis hash but it is bound to no block!");
					else if (maybeGenesis.get() instanceof GenesisBlockDescription gbd)
						new ListChain(chain, gbd.getStartDateTimeUTC(), remote);
					else
						throw new DatabaseException("The type of the genesis block is inconsistent!");
				}
				else
					throw new DatabaseException("The node has no genesis hash it contains some blocks!");
			}
		}
		catch (NoSuchAlgorithmException e) {
			throw new CommandException("Unknown hashing algorithm in the head of the chain of the node at \"" + publicUri() + "\"!", e);
		}
	}

	private class ListChain {
		private final RemotePublicNode remote;
		private final ConsensusConfig<?, ?> config;
		private final LocalDateTime startDateTimeUTC;
		private final String[] heights;
		private final int slotsForHeight;
		private final String[] hashes;
		private final int slotsForHash;
		private final String[] creationDateTimesUTC;
		private final int slotsForCreationDateTimeUTC;
		private final String[] peerPublicKeys;
		private final int slotsForPeerPublicKey;
		private final String[] minerPublicKeys;
		private final int slotsForMinerPublicKey;

		/**
		 * Lists the hashes in {@code chain}, reporting the time of creation of each block.
		 * 
		 * @param chain the chain portion to list
		 * @param startDateTimeUTC the starting moment of the chain
		 * @param the remote node
		 * @throws DatabaseException if the database of the node is corrupted
		 * @throws NoSuchAlgorithmException if some block uses an unknown hashing algorithm
		 * @throws TimeoutException if some connection timed-out
		 * @throws InterruptedException if some connection was interrupted while waiting
		 * @throws ClosedNodeException if the remote node is closed
		 */
		private ListChain(ChainPortion chain, LocalDateTime startDateTimeUTC, RemotePublicNode remote) throws TimeoutException, InterruptedException, ClosedNodeException, NoSuchAlgorithmException, DatabaseException {
			this.config = remote.getConfig();
			this.startDateTimeUTC = startDateTimeUTC;
			this.remote = remote;
			var hashes = chain.getHashes().toArray(byte[][]::new);
			this.heights = new String[1 + hashes.length];
			this.hashes = new String[heights.length];
			this.creationDateTimesUTC = new String[heights.length];
			this.peerPublicKeys = new String[heights.length];
			this.minerPublicKeys = new String[heights.length];
			fillColumns(hashes);
			this.slotsForHeight = Stream.of(heights).mapToInt(String::length).max().getAsInt();
			this.slotsForHash = Stream.of(this.hashes).mapToInt(String::length).max().getAsInt();
			this.slotsForCreationDateTimeUTC = Stream.of(creationDateTimesUTC).mapToInt(String::length).max().getAsInt();
			this.slotsForPeerPublicKey = Stream.of(peerPublicKeys).mapToInt(String::length).max().getAsInt();
			this.slotsForMinerPublicKey = Stream.of(minerPublicKeys).mapToInt(String::length).max().getAsInt();
			printRows();
		}

		private void fillColumns(byte[][] hashes) throws NoSuchAlgorithmException, DatabaseException, TimeoutException, InterruptedException, ClosedNodeException {
			heights[0] = "";
			this.hashes[0] = "block hash (" + config.getHashingForBlocks() + ")";
			creationDateTimesUTC[0] = "created (UTC)";
			peerPublicKeys[0] = "peer public key (" + config.getSignatureForBlocks() + " base58)";
			minerPublicKeys[0] = "miner public key (" + config.getSignatureForDeadlines() + " base58)";
			
			for (int pos = 1; pos < this.hashes.length; pos++) {
				long height = from + hashes.length - pos;
				heights[pos] = height + ":";
				this.hashes[pos] = Hex.toHexString(hashes[hashes.length - pos]);
		
				var maybeBlockDescription = remote.getBlockDescription(hashes[hashes.length - pos]);
				if (maybeBlockDescription.isEmpty()) {
					if (height == 0L) {
						creationDateTimesUTC[pos] = startDateTimeUTC.format(FORMATTER);
						minerPublicKeys[pos] = "---";
					}
					else {
						creationDateTimesUTC[pos] = "unknown";
						minerPublicKeys[pos] = "unknown";
					}
		
					peerPublicKeys[pos] = "unknown";
				}
				else {
					var description = maybeBlockDescription.get();
					creationDateTimesUTC[pos] = startDateTimeUTC.plus(description.getTotalWaitingTime(), ChronoUnit.MILLIS).format(FORMATTER);
					peerPublicKeys[pos] = description.getPublicKeyForSigningBlockBase58();

					if (description instanceof NonGenesisBlockDescription ngbd)
						minerPublicKeys[pos] = ngbd.getDeadline().getProlog().getPublicKeyForSigningDeadlinesBase58();
					else
						minerPublicKeys[pos] = "---";
				}
			}
		}

		private void printRows() {
			IntStream.iterate(0, i -> i + 1).limit(hashes.length).mapToObj(this::format).forEach(System.out::println);
		}

		private String format(int pos) {
			String result = String.format("%s %s  %s  %s  %s",
				rightAlign(heights[pos], slotsForHeight),
				center(hashes[pos], slotsForHash),
				center(creationDateTimesUTC[pos], slotsForCreationDateTimeUTC),
				center(peerPublicKeys[pos], slotsForPeerPublicKey),
				center(minerPublicKeys[pos], slotsForMinerPublicKey));

			return pos == 0 ? Ansi.AUTO.string("@|green " + result + "|@") : result;
		}
	}

	@Override
	protected void execute() throws CommandException {
		execute(this::body);
	}
}