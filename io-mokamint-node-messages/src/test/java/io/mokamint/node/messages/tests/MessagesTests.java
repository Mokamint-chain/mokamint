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

package io.mokamint.node.messages.tests;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigInteger;
import java.net.URI;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Stream;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import io.hotmoka.crypto.HashingAlgorithms;
import io.hotmoka.crypto.SignatureAlgorithms;
import io.hotmoka.testing.AbstractLoggedTests;
import io.hotmoka.websockets.beans.ExceptionMessages;
import io.hotmoka.websockets.beans.api.ExceptionMessage;
import io.mokamint.node.BlockDescriptions;
import io.mokamint.node.Blocks;
import io.mokamint.node.ChainInfos;
import io.mokamint.node.ChainPortions;
import io.mokamint.node.ConsensusConfigBuilders;
import io.mokamint.node.MempoolEntries;
import io.mokamint.node.MempoolInfos;
import io.mokamint.node.MempoolPortions;
import io.mokamint.node.MinerInfos;
import io.mokamint.node.NodeInfos;
import io.mokamint.node.PeerInfos;
import io.mokamint.node.Peers;
import io.mokamint.node.TaskInfos;
import io.mokamint.node.TransactionAddresses;
import io.mokamint.node.Transactions;
import io.mokamint.node.Versions;
import io.mokamint.node.messages.AddPeerMessages;
import io.mokamint.node.messages.AddPeerResultMessages;
import io.mokamint.node.messages.AddTransactionMessages;
import io.mokamint.node.messages.AddTransactionResultMessages;
import io.mokamint.node.messages.GetBlockDescriptionMessages;
import io.mokamint.node.messages.GetBlockDescriptionResultMessages;
import io.mokamint.node.messages.GetBlockMessages;
import io.mokamint.node.messages.GetBlockResultMessages;
import io.mokamint.node.messages.GetChainInfoMessages;
import io.mokamint.node.messages.GetChainInfoResultMessages;
import io.mokamint.node.messages.GetChainPortionMessages;
import io.mokamint.node.messages.GetChainPortionResultMessages;
import io.mokamint.node.messages.GetConfigMessages;
import io.mokamint.node.messages.GetConfigResultMessages;
import io.mokamint.node.messages.GetInfoMessages;
import io.mokamint.node.messages.GetInfoResultMessages;
import io.mokamint.node.messages.GetMempoolInfoMessages;
import io.mokamint.node.messages.GetMempoolInfoResultMessages;
import io.mokamint.node.messages.GetMempoolPortionMessages;
import io.mokamint.node.messages.GetMempoolPortionResultMessages;
import io.mokamint.node.messages.GetMinerInfosMessages;
import io.mokamint.node.messages.GetMinerInfosResultMessages;
import io.mokamint.node.messages.GetPeerInfosMessages;
import io.mokamint.node.messages.GetPeerInfosResultMessages;
import io.mokamint.node.messages.GetTaskInfosMessages;
import io.mokamint.node.messages.GetTaskInfosResultMessages;
import io.mokamint.node.messages.GetTransactionAddressMessages;
import io.mokamint.node.messages.GetTransactionAddressResultMessages;
import io.mokamint.node.messages.GetTransactionMessages;
import io.mokamint.node.messages.GetTransactionRepresentationMessages;
import io.mokamint.node.messages.GetTransactionRepresentationResultMessages;
import io.mokamint.node.messages.GetTransactionResultMessages;
import io.mokamint.node.messages.OpenMinerMessages;
import io.mokamint.node.messages.OpenMinerResultMessages;
import io.mokamint.node.messages.RemoveMinerMessages;
import io.mokamint.node.messages.RemoveMinerResultMessages;
import io.mokamint.node.messages.RemovePeerMessages;
import io.mokamint.node.messages.RemovePeerResultMessages;
import io.mokamint.node.messages.WhisperBlockMessages;
import io.mokamint.node.messages.WhisperPeerMessages;
import io.mokamint.node.messages.WhisperTransactionMessages;
import io.mokamint.nonce.Challenges;
import io.mokamint.nonce.Deadlines;
import io.mokamint.nonce.Prologs;
import jakarta.websocket.DecodeException;

public class MessagesTests extends AbstractLoggedTests {

	@Test
	@DisplayName("getPeers messages are correctly encoded into Json and decoded from Json")
	public void encodeDecodeWorksForGetPeers() throws Exception {
		var getPeersMessage1 = GetPeerInfosMessages.of("id");
		String encoded = new GetPeerInfosMessages.Encoder().encode(getPeersMessage1);
		var getPeersMessage2 = new GetPeerInfosMessages.Decoder().decode(encoded);
		assertEquals(getPeersMessage1, getPeersMessage2);
	}

	@Test
	@DisplayName("getPeersResult messages are correctly encoded into Json and decoded from Json")
	public void encodeDecodeWorksForGetPeersResult() throws Exception {
		var peerInfo1 = PeerInfos.of(Peers.of(URI.create("ws://google.com:8011")), 1234, true);
		var peerInfo2 = PeerInfos.of(Peers.of(URI.create("ws://amazon.it:8024")), 313, false);
		var peerInfo3 = PeerInfos.of(Peers.of(URI.create("ws://panarea.io:8025")), 112, true);
		var getPeersResultMessage1 = GetPeerInfosResultMessages.of(Stream.of(peerInfo1, peerInfo2, peerInfo3), "id");
		String encoded = new GetPeerInfosResultMessages.Encoder().encode(getPeersResultMessage1);
		var getPeersResultMessage2 = new GetPeerInfosResultMessages.Decoder().decode(encoded);
		assertEquals(getPeersResultMessage1, getPeersResultMessage2);
	}

	@Test
	@DisplayName("getBlock messages are correctly encoded into Json and decoded from Json")
	public void encodeDecodeWorksForGetBlock() throws Exception {
		var getBlockMessage1 = GetBlockMessages.of(new byte[] { 1, 2, 3, 4, 5 }, "id");
		String encoded = new GetBlockMessages.Encoder().encode(getBlockMessage1);
		var getBlockMessage2 = new GetBlockMessages.Decoder().decode(encoded);
		assertEquals(getBlockMessage1, getBlockMessage2);
	}

	@Test
	@DisplayName("non-empty getBlockResult messages are correctly encoded into Json and decoded from Json")
	public void encodeDecodeWorksForGetBlockResultNonEmpty() throws Exception {
		var hashingForDeadlines = HashingAlgorithms.shabal256();
		var hashingForGenerations = HashingAlgorithms.sha256();
		var hashingForBlocks = HashingAlgorithms.sha256();
		var generationSignature = new byte[hashingForGenerations.length()];
		for (int pos = 0; pos < generationSignature.length; pos++)
			generationSignature[pos] = (byte) (42 + pos);
		var value = new byte[hashingForDeadlines.length()];
		for (int pos = 0; pos < value.length; pos++)
			value[pos] = (byte) pos;
		var hashingOfPreviousBlock = new byte[hashingForBlocks.length()];
		for (int pos = 0; pos < hashingOfPreviousBlock.length; pos++)
			hashingOfPreviousBlock[pos] = (byte) (17 + pos);
		var ed25519 = SignatureAlgorithms.ed25519();
		var nodeKeyPair = ed25519.getKeyPair();
		var plotKeyPair = ed25519.getKeyPair();
		var prolog = Prologs.of("octopus", ed25519, nodeKeyPair.getPublic(), ed25519, plotKeyPair.getPublic(), new byte[0]);
		var deadline = Deadlines.of(prolog, 13, value, Challenges.of(11, generationSignature, hashingForDeadlines, hashingForGenerations), plotKeyPair.getPrivate());
		var transaction1 = Transactions.of(new byte[] { 13, 17, 23, 31 });
		var transaction2 = Transactions.of(new byte[] { 5, 6, 7 });
		var transaction3 = Transactions.of(new byte[] {});
		var block = Blocks.of(BlockDescriptions.of(13, BigInteger.TEN, 1234L, 1100L, BigInteger.valueOf(13011973), deadline, hashingOfPreviousBlock, 4000, 20000, hashingForBlocks, HashingAlgorithms.sha256()),
			Stream.of(transaction1, transaction2, transaction3), new byte[0], nodeKeyPair.getPrivate());
		var getBlockResultMessage1 = GetBlockResultMessages.of(Optional.of(block), "id");
		String encoded = new GetBlockResultMessages.Encoder().encode(getBlockResultMessage1);
		var getBlockResultMessage2 = new GetBlockResultMessages.Decoder().decode(encoded);
		assertEquals(getBlockResultMessage1, getBlockResultMessage2);
	}

	@Test
	@DisplayName("empty getBlockResult messages are correctly encoded into Json and decoded from Json")
	public void encodeDecodeWorksForGetBlockResultEmpty() throws Exception {
		var getBlockResultMessage1 = GetBlockResultMessages.of(Optional.empty(), "id");
		String encoded = new GetBlockResultMessages.Encoder().encode(getBlockResultMessage1);
		var getBlockResultMessage2 = new GetBlockResultMessages.Decoder().decode(encoded);
		assertEquals(getBlockResultMessage1, getBlockResultMessage2);
	}

	@Test
	@DisplayName("getBlockDescription messages are correctly encoded into Json and decoded from Json")
	public void encodeDecodeWorksForGetBlockDescription() throws Exception {
		var getBlockDescriptionMessage1 = GetBlockDescriptionMessages.of(new byte[] { 1, 2, 3, 4, 5 }, "id");
		String encoded = new GetBlockDescriptionMessages.Encoder().encode(getBlockDescriptionMessage1);
		var getBlockDescriptionMessage2 = new GetBlockDescriptionMessages.Decoder().decode(encoded);
		assertEquals(getBlockDescriptionMessage1, getBlockDescriptionMessage2);
	}

	@Test
	@DisplayName("non-empty getBlockDescriptionResult messages are correctly encoded into Json and decoded from Json")
	public void encodeDecodeWorksForGetBlockDescriptionResultNonEmpty() throws Exception {
		var hashingForDeadlines = HashingAlgorithms.shabal256();
		var hashingForGenerations = HashingAlgorithms.sha256();
		var hashingForBlocks = HashingAlgorithms.sha256();
		var generationSignature = new byte[hashingForGenerations.length()];
		for (int pos = 0; pos < generationSignature.length; pos++)
			generationSignature[pos] = (byte) (42 + pos);
		var value = new byte[hashingForDeadlines.length()];
		for (int pos = 0; pos < value.length; pos++)
			value[pos] = (byte) pos;
		var hashingOfPreviousBlock = new byte[hashingForBlocks.length()];
		for (int pos = 0; pos < hashingOfPreviousBlock.length; pos++)
			hashingOfPreviousBlock[pos] = (byte) (17 + pos);
		var ed25519 = SignatureAlgorithms.ed25519();
		var nodeKeyPair = ed25519.getKeyPair();
		var plotKeyPair = ed25519.getKeyPair();
		var prolog = Prologs.of("octopus", ed25519, nodeKeyPair.getPublic(), ed25519, plotKeyPair.getPublic(), new byte[0]);
		var deadline = Deadlines.of(prolog, 13, value, Challenges.of(11, generationSignature, hashingForDeadlines, hashingForGenerations), plotKeyPair.getPrivate());
		var block = BlockDescriptions.of(13, BigInteger.TEN, 1234L, 1100L, BigInteger.valueOf(13011973), deadline, hashingOfPreviousBlock, 4000, 20000, hashingForBlocks, HashingAlgorithms.sha256());
		var getBlockDescriptionResultMessage1 = GetBlockDescriptionResultMessages.of(Optional.of(block), "id");
		String encoded = new GetBlockDescriptionResultMessages.Encoder().encode(getBlockDescriptionResultMessage1);
		var getBlockDescriptionResultMessage2 = new GetBlockDescriptionResultMessages.Decoder().decode(encoded);
		assertEquals(getBlockDescriptionResultMessage1, getBlockDescriptionResultMessage2);
	}

	@Test
	@DisplayName("empty getBlockDescriptionResult messages are correctly encoded into Json and decoded from Json")
	public void encodeDecodeWorksForGetBlockDescriptionResultEmpty() throws Exception {
		var getBlockDescriptionResultMessage1 = GetBlockDescriptionResultMessages.of(Optional.empty(), "id");
		String encoded = new GetBlockDescriptionResultMessages.Encoder().encode(getBlockDescriptionResultMessage1);
		var getBlockDescriptionResultMessage2 = new GetBlockDescriptionResultMessages.Decoder().decode(encoded);
		assertEquals(getBlockDescriptionResultMessage1, getBlockDescriptionResultMessage2);
	}

	@Test
	@DisplayName("getChainPortion messages are correctly encoded into Json and decoded from Json")
	public void encodeDecodeWorksForGetChainPortion() throws Exception {
		var getChainPortionMessage1 = GetChainPortionMessages.of(13, 20, "id");
		String encoded = new GetChainPortionMessages.Encoder().encode(getChainPortionMessage1);
		var getChainPortionMessage2 = new GetChainPortionMessages.Decoder().decode(encoded);
		assertEquals(getChainPortionMessage1, getChainPortionMessage2);
	}

	@Test
	@DisplayName("getChainPortion result messages are correctly encoded into Json and decoded from Json")
	public void encodeDecodeWorksForGetChainPortionResult() throws Exception {
		var chain = ChainPortions.of(Stream.of(new byte[] { 1, 2, 3 }, new byte[] { 20, 50, 70, 88 }));
		var getChainPortionResultMessage1 = GetChainPortionResultMessages.of(chain, "id");
		String encoded = new GetChainPortionResultMessages.Encoder().encode(getChainPortionResultMessage1);
		var getChainPortionResultMessage2 = new GetChainPortionResultMessages.Decoder().decode(encoded);
		assertEquals(getChainPortionResultMessage1, getChainPortionResultMessage2);
	}

	@Test
	@DisplayName("getMempoolPortion messages are correctly encoded into Json and decoded from Json")
	public void encodeDecodeWorksForGetMempoolPortion() throws Exception {
		var getMempoolPortionMessage1 = GetMempoolPortionMessages.of(13, 20, "id");
		String encoded = new GetMempoolPortionMessages.Encoder().encode(getMempoolPortionMessage1);
		var getMempoolPortionMessage2 = new GetMempoolPortionMessages.Decoder().decode(encoded);
		assertEquals(getMempoolPortionMessage1, getMempoolPortionMessage2);
	}

	@Test
	@DisplayName("getMempoolPortion result messages are correctly encoded into Json and decoded from Json")
	public void encodeDecodeWorksForGetMempoolPortionResult() throws Exception {
		var info1 = MempoolEntries.of(new byte[] { 1, 2, 3 }, 13L);
		var info2 = MempoolEntries.of(new byte[] { 20, 50, 70, 88 }, 17L);
		var mempool = MempoolPortions.of(Stream.of(info1, info2));
		var getMempoolPortionResultMessage1 = GetMempoolPortionResultMessages.of(mempool, "id");
		String encoded = new GetMempoolPortionResultMessages.Encoder().encode(getMempoolPortionResultMessage1);
		var getMempoolPortionResultMessage2 = new GetMempoolPortionResultMessages.Decoder().decode(encoded);
		assertEquals(getMempoolPortionResultMessage1, getMempoolPortionResultMessage2);
	}

	@Test
	@DisplayName("getPeerInfos messages are correctly encoded into Json and decoded from Json")
	public void encodeDecodeWorksForGetPeerInfos() throws Exception {
		var getPeerInfosMessage1 = GetPeerInfosMessages.of("id");
		String encoded = new GetPeerInfosMessages.Encoder().encode(getPeerInfosMessage1);
		var getPeerInfosMessage2 = new GetPeerInfosMessages.Decoder().decode(encoded);
		assertEquals(getPeerInfosMessage1, getPeerInfosMessage2);
	}

	@Test
	@DisplayName("getPeerInfosResult messages are correctly encoded into Json and decoded from Json")
	public void encodeDecodeWorksForGetPeerInfosResult() throws Exception {
		var peers = Stream.of(PeerInfos.of(Peers.of(URI.create("ws://www.hotmoka.io")), 100L, true),
				PeerInfos.of(Peers.of(URI.create("ws://www.mokamint.io:8030")), 123L, false));
		var getPeerInfosResultMessage1 = GetPeerInfosResultMessages.of(peers, "id");
		String encoded = new GetPeerInfosResultMessages.Encoder().encode(getPeerInfosResultMessage1);
		var getPeerInfosResultMessage2 = new GetPeerInfosResultMessages.Decoder().decode(encoded);
		assertEquals(getPeerInfosResultMessage1, getPeerInfosResultMessage2);
	}

	@Test
	@DisplayName("getMinerInfos messages are correctly encoded into Json and decoded from Json")
	public void encodeDecodeWorksForGetMinerInfos() throws Exception {
		var getMinerInfosMessage1 = GetMinerInfosMessages.of("id");
		String encoded = new GetMinerInfosMessages.Encoder().encode(getMinerInfosMessage1);
		var getMinerInfosMessage2 = new GetMinerInfosMessages.Decoder().decode(encoded);
		assertEquals(getMinerInfosMessage1, getMinerInfosMessage2);
	}

	@Test
	@DisplayName("getMinerInfosResult messages are correctly encoded into Json and decoded from Json")
	public void encodeDecodeWorksForGetMinerInfosResult() throws Exception {
		var miners = Stream.of(MinerInfos.of(UUID.randomUUID(), 100L, "a miner"),
				MinerInfos.of(UUID.randomUUID(), 123L, "another miner"));
		var getMinerInfosResultMessage1 = GetMinerInfosResultMessages.of(miners, "id");
		String encoded = new GetMinerInfosResultMessages.Encoder().encode(getMinerInfosResultMessage1);
		var getMinerInfosResultMessage2 = new GetMinerInfosResultMessages.Decoder().decode(encoded);
		assertEquals(getMinerInfosResultMessage1, getMinerInfosResultMessage2);
	}

	@Test
	@DisplayName("getTaskInfos messages are correctly encoded into Json and decoded from Json")
	public void encodeDecodeWorksForGetTaskInfos() throws Exception {
		var getTaskInfosMessage1 = GetTaskInfosMessages.of("id");
		String encoded = new GetTaskInfosMessages.Encoder().encode(getTaskInfosMessage1);
		var getTaskInfosMessage2 = new GetTaskInfosMessages.Decoder().decode(encoded);
		assertEquals(getTaskInfosMessage1, getTaskInfosMessage2);
	}

	@Test
	@DisplayName("getTaskInfosResult messages are correctly encoded into Json and decoded from Json")
	public void encodeDecodeWorksForGetTaskInfosResult() throws Exception {
		var tasks = Stream.of(TaskInfos.of("a beautiful task"), TaskInfos.of("another beautiful task"));
		var getTaskInfosResultMessage1 = GetTaskInfosResultMessages.of(tasks, "id");
		String encoded = new GetTaskInfosResultMessages.Encoder().encode(getTaskInfosResultMessage1);
		var getTaskInfosResultMessage2 = new GetTaskInfosResultMessages.Decoder().decode(encoded);
		assertEquals(getTaskInfosResultMessage1, getTaskInfosResultMessage2);
	}

	@Test
	@DisplayName("getConfig messages are correctly encoded into Json and decoded from Json")
	public void encodeDecodeWorksForGetConfig() throws Exception {
		var getConfigMessage1 = GetConfigMessages.of("id");
		String encoded = new GetConfigMessages.Encoder().encode(getConfigMessage1);
		var getConfigMessage2 = new GetConfigMessages.Decoder().decode(encoded);
		assertEquals(getConfigMessage1, getConfigMessage2);
	}

	@Test
	@DisplayName("getInfo messages are correctly encoded into Json and decoded from Json")
	public void encodeDecodeWorksForGetInfo() throws Exception {
		var getInfoMessage1 = GetInfoMessages.of("id");
		String encoded = new GetInfoMessages.Encoder().encode(getInfoMessage1);
		var getInfoMessage2 = new GetInfoMessages.Decoder().decode(encoded);
		assertEquals(getInfoMessage1, getInfoMessage2);
	}

	@Test
	@DisplayName("getInfo result messages are correctly encoded into Json and decoded from Json")
	public void encodeDecodeWorksForGetInfoResult() throws Exception {
		var info = NodeInfos.of(Versions.of(3, 4, 5), UUID.randomUUID(), LocalDateTime.now(ZoneId.of("UTC")));
		var getInfoResultMessage1 = GetInfoResultMessages.of(info, "id");
		String encoded = new GetInfoResultMessages.Encoder().encode(getInfoResultMessage1);
		var getInfoResultMessage2 = new GetInfoResultMessages.Decoder().decode(encoded);
		assertEquals(getInfoResultMessage1, getInfoResultMessage2);
	}

	@Test
	@DisplayName("getMempoolInfo messages are correctly encoded into Json and decoded from Json")
	public void encodeDecodeWorksForGetMempoolInfo() throws Exception {
		var getMempoolInfoMessage1 = GetMempoolInfoMessages.of("id");
		String encoded = new GetMempoolInfoMessages.Encoder().encode(getMempoolInfoMessage1);
		var getMempoolInfoMessage2 = new GetMempoolInfoMessages.Decoder().decode(encoded);
		assertEquals(getMempoolInfoMessage1, getMempoolInfoMessage2);
	}

	@Test
	@DisplayName("getMempoolInfo result messages are correctly encoded into Json and decoded from Json")
	public void encodeDecodeWorksForGetMempoolInfoResult() throws Exception {
		var info = MempoolInfos.of(1317L);
		var getMempoolInfoResultMessage1 = GetMempoolInfoResultMessages.of(info, "id");
		String encoded = new GetMempoolInfoResultMessages.Encoder().encode(getMempoolInfoResultMessage1);
		var getMempoolInfoResultMessage2 = new GetMempoolInfoResultMessages.Decoder().decode(encoded);
		assertEquals(getMempoolInfoResultMessage1, getMempoolInfoResultMessage2);
	}

	@Test
	@DisplayName("getConfig result messages are correctly encoded into Json and decoded from Json")
	public void encodeDecodeWorksForGetConfigResult() throws Exception {
		var config = ConsensusConfigBuilders.defaults().build();
		var getConfigResultMessage1 = GetConfigResultMessages.of(config, "id");
		String encoded = new GetConfigResultMessages.Encoder().encode(getConfigResultMessage1);
		var getConfigResultMessage2 = new GetConfigResultMessages.Decoder().decode(encoded);
		assertEquals(getConfigResultMessage1, getConfigResultMessage2);
	}

	@Test
	@DisplayName("exception result messages are correctly encoded into Json and decoded from Json")
	public void encodeDecodeWorksForExceptionResult() throws Exception {
		var exceptionResultMessage1 = ExceptionMessages.of(NoSuchAlgorithmException.class, Optional.of("something went wrong"), "id");
		String encoded = new ExceptionMessages.Encoder().encode(exceptionResultMessage1);
		var exceptionResultMessage2 = new ExceptionMessages.Decoder().decode(encoded);
		assertEquals(exceptionResultMessage1, exceptionResultMessage2);
	}

	@Test
	@DisplayName("getChainInfo messages are correctly encoded into Json and decoded from Json")
	public void encodeDecodeWorksForGetChainInfo() throws Exception {
		var getChainInfo1 = GetChainInfoMessages.of("id");
		String encoded = new GetChainInfoMessages.Encoder().encode(getChainInfo1);
		var getChainInfo2 = new GetChainInfoMessages.Decoder().decode(encoded);
		assertEquals(getChainInfo1, getChainInfo2);
	}

	@Test
	@DisplayName("getChainInfo result messages are correctly encoded into Json and decoded from Json")
	public void encodeDecodeWorksForGetChainInfoResult() throws Exception {
		var info = ChainInfos.of(1973L, Optional.of(new byte[] { 1, 2, 3, 4 }), Optional.of(new byte[] { 3, 7, 8, 11 }), Optional.of(new byte[] { 13, 17, 19 }));
		var getChainInfoResultMessage1 = GetChainInfoResultMessages.of(info, "id");
		String encoded = new GetChainInfoResultMessages.Encoder().encode(getChainInfoResultMessage1);
		var getChainInfoResultMessage2 = new GetChainInfoResultMessages.Decoder().decode(encoded);
		assertEquals(getChainInfoResultMessage1, getChainInfoResultMessage2);
	}

	@Test
	@DisplayName("add transaction messages are correctly encoded into Json and decoded from Json")
	public void encodeDecodeWorksForAddTransaction() throws Exception {
		var transaction = Transactions.of(new byte[] { 1, 2, 3, 4, 5 });
		var addTransactionMessage1 = AddTransactionMessages.of(transaction, "id");
		String encoded = new AddTransactionMessages.Encoder().encode(addTransactionMessage1);
		var addTransactionMessage2 = new AddTransactionMessages.Decoder().decode(encoded);
		assertEquals(addTransactionMessage1, addTransactionMessage2);
	}

	@Test
	@DisplayName("add transaction result messages are correctly encoded into Json and decoded from Json")
	public void encodeDecodeWorksForAddTransactionResult() throws Exception {
		var addTransactionResultMessage1 = AddTransactionResultMessages.of(MempoolEntries.of(new byte[] { 1, 2, 3 }, 17L), "id");
		String encoded = new AddTransactionResultMessages.Encoder().encode(addTransactionResultMessage1);
		var addTransactionResultMessage2 = new AddTransactionResultMessages.Decoder().decode(encoded);
		assertEquals(addTransactionResultMessage1, addTransactionResultMessage2);
	}

	@Test
	@DisplayName("add peer messages are correctly encoded into Json and decoded from Json")
	public void encodeDecodeWorksForAddPeers() throws Exception {
		var addPeers1 = AddPeerMessages.of(Peers.of(URI.create("ws://google.com:8011")), "id");
		String encoded = new AddPeerMessages.Encoder().encode(addPeers1);
		var addPeers2 = new AddPeerMessages.Decoder().decode(encoded);
		assertEquals(addPeers1, addPeers2);
	}

	@Test
	@DisplayName("remove peer messages are correctly encoded into Json and decoded from Json")
	public void encodeDecodeWorksForRemovePeer() throws Exception {
		var removePeers1 = RemovePeerMessages.of(Peers.of(URI.create("ws://google.com:8011")), "id");
		String encoded = new RemovePeerMessages.Encoder().encode(removePeers1);
		var removePeers2 = new RemovePeerMessages.Decoder().decode(encoded);
		assertEquals(removePeers1, removePeers2);
	}

	@Test
	@DisplayName("add peer non-empty result messages are correctly encoded into Json and decoded from Json")
	public void encodeDecodeWorksForNonEmptyAddPeerResult() throws Exception {
		var addPeerResultMessage1 = AddPeerResultMessages.of(Optional.of(PeerInfos.of(Peers.of(URI.create("ws://www.mokamint.io")), 800, true)), "id");
		String encoded = new AddPeerResultMessages.Encoder().encode(addPeerResultMessage1);
		var addPeerResultMessage2 = new AddPeerResultMessages.Decoder().decode(encoded);
		assertEquals(addPeerResultMessage1, addPeerResultMessage2);
	}

	@Test
	@DisplayName("addPeer empty result messages are correctly encoded into Json and decoded from Json")
	public void encodeDecodeWorksForEmptyAddPeerResult() throws Exception {
		var addPeerResultMessage1 = AddPeerResultMessages.of(Optional.empty(), "id");
		String encoded = new AddPeerResultMessages.Encoder().encode(addPeerResultMessage1);
		var addPeerResultMessage2 = new AddPeerResultMessages.Decoder().decode(encoded);
		assertEquals(addPeerResultMessage1, addPeerResultMessage2);
	}

	@Test
	@DisplayName("removePeer result messages are correctly encoded into Json and decoded from Json")
	public void encodeDecodeWorksForRemovePeerResult() throws Exception {
		var removePeerResultMessage1 = RemovePeerResultMessages.of(true, "id");
		String encoded = new RemovePeerResultMessages.Encoder().encode(removePeerResultMessage1);
		var removePeerResultMessage2 = new RemovePeerResultMessages.Decoder().decode(encoded);
		assertEquals(removePeerResultMessage1, removePeerResultMessage2);
	}

	@Test
	@DisplayName("openMiner messages are correctly encoded into Json and decoded from Json")
	public void encodeDecodeWorksForOpenMiner() throws Exception {
		var openMiner1 = OpenMinerMessages.of(8025, "id");
		String encoded = new OpenMinerMessages.Encoder().encode(openMiner1);
		var openMiner2 = new OpenMinerMessages.Decoder().decode(encoded);
		assertEquals(openMiner1, openMiner2);
	}

	@Test
	@DisplayName("openMiner non-empty result messages are correctly encoded into Json and decoded from Json")
	public void encodeDecodeWorksForOpenMinerNonEmptyResult() throws Exception {
		var info = MinerInfos.of(UUID.randomUUID(), 1234, "a breautiful miner");
		var openMinerResultMessage1 = OpenMinerResultMessages.of(Optional.of(info), "id");
		String encoded = new OpenMinerResultMessages.Encoder().encode(openMinerResultMessage1);
		var openMinerResultMessage2 = new OpenMinerResultMessages.Decoder().decode(encoded);
		assertEquals(openMinerResultMessage1, openMinerResultMessage2);
	}

	@Test
	@DisplayName("openMiner empty result messages are correctly encoded into Json and decoded from Json")
	public void encodeDecodeWorksForOpenMinerEmptyResult() throws Exception {
		var openMinerResultMessage1 = OpenMinerResultMessages.of(Optional.empty(), "id");
		String encoded = new OpenMinerResultMessages.Encoder().encode(openMinerResultMessage1);
		var openMinerResultMessage2 = new OpenMinerResultMessages.Decoder().decode(encoded);
		assertEquals(openMinerResultMessage1, openMinerResultMessage2);
	}

	@Test
	@DisplayName("closeMiner messages are correctly encoded into Json and decoded from Json")
	public void encodeDecodeWorksForCloseMiner() throws Exception {
		var closeMiner1 = RemoveMinerMessages.of(UUID.randomUUID(), "id");
		String encoded = new RemoveMinerMessages.Encoder().encode(closeMiner1);
		var closeMiner2 = new RemoveMinerMessages.Decoder().decode(encoded);
		assertEquals(closeMiner1, closeMiner2);
	}

	@Test
	@DisplayName("closeMiner result messages are correctly encoded into Json and decoded from Json")
	public void encodeDecodeWorksForCloseMinerResult() throws Exception {
		var closeMinerResultMessage1 = RemoveMinerResultMessages.of(true, "id");
		String encoded = new RemoveMinerResultMessages.Encoder().encode(closeMinerResultMessage1);
		var closeMinerResultMessage2 = new RemoveMinerResultMessages.Decoder().decode(encoded);
		assertEquals(closeMinerResultMessage1, closeMinerResultMessage2);
	}

	@Test
	@DisplayName("whisper peers messages are correctly encoded into Json and decoded from Json")
	public void encodeDecodeWorksForWhisperPeers() throws Exception {
		var peer1 = Peers.of(URI.create("ws://google.com:8011"));
		var whisperPeersMessage1 = WhisperPeerMessages.of(peer1, "id");
		String encoded = new WhisperPeerMessages.Encoder().encode(whisperPeersMessage1);
		var whisperPeersMessage2 = new WhisperPeerMessages.Decoder().decode(encoded);
		assertEquals(whisperPeersMessage1, whisperPeersMessage2);
	}

	@Test
	@DisplayName("whisper block messages are correctly encoded into Json and decoded from Json")
	public void encodeDecodeWorksForWhisperBlock() throws Exception {
		var hashingForDeadlines = HashingAlgorithms.shabal256();
		var hashingForGenerations = HashingAlgorithms.sha256();
		var hashingForBlocks = HashingAlgorithms.sha256();
		var generationSignature = new byte[hashingForGenerations.length()];
		for (int pos = 0; pos < generationSignature.length; pos++)
			generationSignature[pos] = (byte) (42 + pos);
		var value = new byte[hashingForDeadlines.length()];
		for (int pos = 0; pos < value.length; pos++)
			value[pos] = (byte) pos;
		var hashingOfPreviousBlock = new byte[hashingForBlocks.length()];
		for (int pos = 0; pos < hashingOfPreviousBlock.length; pos++)
			hashingOfPreviousBlock[pos] = (byte) (17 + pos);
		var ed25519 = SignatureAlgorithms.ed25519();
		var nodeKeyPair = ed25519.getKeyPair();
		var plotKeyPair = ed25519.getKeyPair();
		var prolog = Prologs.of("octopus", ed25519, nodeKeyPair.getPublic(), ed25519, plotKeyPair.getPublic(), new byte[0]);
		var deadline = Deadlines.of(prolog, 13, value, Challenges.of(11, generationSignature, hashingForDeadlines, hashingForGenerations), plotKeyPair.getPrivate());
		var transaction1 = Transactions.of(new byte[] { 13, 17, 23, 31 });
		var transaction2 = Transactions.of(new byte[] { 5, 6, 7 });
		var transaction3 = Transactions.of(new byte[] {});
		var block = Blocks.of(BlockDescriptions.of(13, BigInteger.TEN, 1234L, 1100L, BigInteger.valueOf(13011973), deadline, hashingOfPreviousBlock, 4000, 20000, hashingForBlocks, HashingAlgorithms.sha256()),
			Stream.of(transaction1, transaction2, transaction3), new byte[0], nodeKeyPair.getPrivate());
		var whisperBlockMessage1 = WhisperBlockMessages.of(block, "id");
		String encoded = new WhisperBlockMessages.Encoder().encode(whisperBlockMessage1);
		var whisperBlockMessage2 = new WhisperBlockMessages.Decoder().decode(encoded);
		assertEquals(whisperBlockMessage1, whisperBlockMessage2);
	}

	@Test
	@DisplayName("whisper transaction messages are correctly encoded into Json and decoded from Json")
	public void encodeDecodeWorksForWhisperTransaction() throws Exception {
		var transaction = Transactions.of(new byte[] { 1, 2, 3, 4, 5, 6});
		var whisperTransactionMessage1 = WhisperTransactionMessages.of(transaction, "id");
		String encoded = new WhisperTransactionMessages.Encoder().encode(whisperTransactionMessage1);
		var whisperTransactionMessage2 = new WhisperTransactionMessages.Decoder().decode(encoded);
		assertEquals(whisperTransactionMessage1, whisperTransactionMessage2);
	}

	@Test
	@DisplayName("getTransaction messages are correctly encoded into Json and decoded from Json")
	public void encodeDecodeWorksForGetTransaction() throws Exception {
		var getTransactionMessage1 = GetTransactionMessages.of(new byte[] { 1, 2, 3, 4, 5 }, "id");
		String encoded = new GetTransactionMessages.Encoder().encode(getTransactionMessage1);
		var getTransactionMessage2 = new GetTransactionMessages.Decoder().decode(encoded);
		assertEquals(getTransactionMessage1, getTransactionMessage2);
	}

	@Test
	@DisplayName("non-empty getTransactionResult messages are correctly encoded into Json and decoded from Json")
	public void encodeDecodeWorksForGetTransactionResultNonEmpty() throws Exception {
		var tx = Transactions.of(new byte[] { 1 ,2, 3, 4 });
		var getTransactionResultMessage1 = GetTransactionResultMessages.of(Optional.of(tx), "id");
		String encoded = new GetTransactionResultMessages.Encoder().encode(getTransactionResultMessage1);
		var getTransactionResultMessage2 = new GetTransactionResultMessages.Decoder().decode(encoded);
		assertEquals(getTransactionResultMessage1, getTransactionResultMessage2);
	}

	@Test
	@DisplayName("empty getTransactionResult messages are correctly encoded into Json and decoded from Json")
	public void encodeDecodeWorksForGetTransactionResultEmpty() throws Exception {
		var getTransactionResultMessage1 = GetTransactionResultMessages.of(Optional.empty(), "id");
		String encoded = new GetTransactionResultMessages.Encoder().encode(getTransactionResultMessage1);
		var getTransactionResultMessage2 = new GetTransactionResultMessages.Decoder().decode(encoded);
		assertEquals(getTransactionResultMessage1, getTransactionResultMessage2);
	}

	@Test
	@DisplayName("getTransactionRepresentation messages are correctly encoded into Json and decoded from Json")
	public void encodeDecodeWorksForGetTransactionRepresentation() throws Exception {
		var getTransactionRepresentationMessage1 = GetTransactionRepresentationMessages.of(new byte[] { 1, 2, 3, 4, 5 }, "id");
		String encoded = new GetTransactionRepresentationMessages.Encoder().encode(getTransactionRepresentationMessage1);
		var getTransactionRepresentationMessage2 = new GetTransactionRepresentationMessages.Decoder().decode(encoded);
		assertEquals(getTransactionRepresentationMessage1, getTransactionRepresentationMessage2);
	}

	@Test
	@DisplayName("non-empty getTransactionRepresentationResult messages are correctly encoded into Json and decoded from Json")
	public void encodeDecodeWorksForGetTransactionRepresentationResultNonEmpty() throws Exception {
		var getTransactionRepresentationResultMessage1 = GetTransactionRepresentationResultMessages.of(Optional.of("hello"), "id");
		String encoded = new GetTransactionRepresentationResultMessages.Encoder().encode(getTransactionRepresentationResultMessage1);
		var getTransactionRepresentationResultMessage2 = new GetTransactionRepresentationResultMessages.Decoder().decode(encoded);
		assertEquals(getTransactionRepresentationResultMessage1, getTransactionRepresentationResultMessage2);
	}

	@Test
	@DisplayName("empty getTransactionRepresentationResult messages are correctly encoded into Json and decoded from Json")
	public void encodeDecodeWorksForGetTransactionRepresentationResultEmpty() throws Exception {
		var getTransactionRepresentationResultMessage1 = GetTransactionRepresentationResultMessages.of(Optional.empty(), "id");
		String encoded = new GetTransactionRepresentationResultMessages.Encoder().encode(getTransactionRepresentationResultMessage1);
		var getTransactionRepresentationResultMessage2 = new GetTransactionRepresentationResultMessages.Decoder().decode(encoded);
		assertEquals(getTransactionRepresentationResultMessage1, getTransactionRepresentationResultMessage2);
	}

	@Test
	@DisplayName("exception result messages cannot be decoded from Json if the class type is not an exception")
	public void decodeFailsForExceptionResultIfNotException() {
		String encoded = "{\"className\":\"java.lang.String\",\"message\":\"something went wrong\", \"type\":\"" + ExceptionMessage.class.getName() + "\",\"id\":\"id\"}";
		DecodeException e = assertThrows(DecodeException.class, () -> new ExceptionMessages.Decoder().decode(encoded));
		assertTrue(e.getCause() instanceof ClassCastException);
	}

	@Test
	@DisplayName("getTransactionAddress messages are correctly encoded into Json and decoded from Json")
	public void encodeDecodeWorksForGetTransactionAddress() throws Exception {
		var getTransactionAddressMessage1 = GetTransactionAddressMessages.of(new byte[] { 13, 1, 19, 73 }, "id");
		String encoded = new GetTransactionAddressMessages.Encoder().encode(getTransactionAddressMessage1);
		var getTransactionAddressMessage2 = new GetTransactionAddressMessages.Decoder().decode(encoded);
		assertEquals(getTransactionAddressMessage1, getTransactionAddressMessage2);
	}

	@Test
	@DisplayName("non-empty getTransactionAddressResult messages are correctly encoded into Json and decoded from Json")
	public void encodeDecodeWorksForGetTransactionAddressResultNonEmpty() throws Exception {
		var address = TransactionAddresses.of(new byte[] { 13, 1, 19, 73 }, 17);
		var getTransactionAddressResultMessage1 = GetTransactionAddressResultMessages.of(Optional.of(address), "id");
		String encoded = new GetTransactionAddressResultMessages.Encoder().encode(getTransactionAddressResultMessage1);
		var getTransactionAddressResultMessage2 = new GetTransactionAddressResultMessages.Decoder().decode(encoded);
		assertEquals(getTransactionAddressResultMessage1, getTransactionAddressResultMessage2);
	}

	@Test
	@DisplayName("empty getTransactionAddressResult messages are correctly encoded into Json and decoded from Json")
	public void encodeDecodeWorksForGetTransactionAddressResultEmpty() throws Exception {
		var getTransactionAddressResultMessage1 = GetTransactionAddressResultMessages.of(Optional.empty(), "id");
		String encoded = new GetTransactionAddressResultMessages.Encoder().encode(getTransactionAddressResultMessage1);
		var getTransactionAddressResultMessage2 = new GetTransactionAddressResultMessages.Decoder().decode(encoded);
		assertEquals(getTransactionAddressResultMessage1, getTransactionAddressResultMessage2);
	}
}