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
import java.net.URISyntaxException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.SignatureException;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Stream;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import io.hotmoka.crypto.HashingAlgorithms;
import io.hotmoka.crypto.SignatureAlgorithms;
import io.hotmoka.crypto.api.HashingAlgorithm;
import io.hotmoka.testing.AbstractLoggedTests;
import io.mokamint.node.BlockDescriptions;
import io.mokamint.node.Blocks;
import io.mokamint.node.ChainInfos;
import io.mokamint.node.ChainPortions;
import io.mokamint.node.ConsensusConfigBuilders;
import io.mokamint.node.MinerInfos;
import io.mokamint.node.NodeInfos;
import io.mokamint.node.PeerInfos;
import io.mokamint.node.Peers;
import io.mokamint.node.TaskInfos;
import io.mokamint.node.Transactions;
import io.mokamint.node.Versions;
import io.mokamint.node.messages.AddPeerMessages;
import io.mokamint.node.messages.AddPeerResultMessages;
import io.mokamint.node.messages.CloseMinerMessages;
import io.mokamint.node.messages.CloseMinerResultMessages;
import io.mokamint.node.messages.ExceptionMessages;
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
import io.mokamint.node.messages.GetMinerInfosMessages;
import io.mokamint.node.messages.GetMinerInfosResultMessages;
import io.mokamint.node.messages.GetPeerInfosMessages;
import io.mokamint.node.messages.GetPeerInfosResultMessages;
import io.mokamint.node.messages.GetTaskInfosMessages;
import io.mokamint.node.messages.GetTaskInfosResultMessages;
import io.mokamint.node.messages.OpenMinerMessages;
import io.mokamint.node.messages.OpenMinerResultMessages;
import io.mokamint.node.messages.AddTransactionMessages;
import io.mokamint.node.messages.AddTransactionResultMessages;
import io.mokamint.node.messages.RemovePeerMessages;
import io.mokamint.node.messages.RemovePeerResultMessages;
import io.mokamint.node.messages.WhisperBlockMessages;
import io.mokamint.node.messages.WhisperPeersMessages;
import io.mokamint.node.messages.api.ExceptionMessage;
import io.mokamint.nonce.Deadlines;
import io.mokamint.nonce.Prologs;
import jakarta.websocket.DecodeException;
import jakarta.websocket.EncodeException;

public class MessagesTests extends AbstractLoggedTests {

	@Test
	@DisplayName("getPeers messages are correctly encoded into Json and decoded from Json")
	public void encodeDecodeWorksForGetPeers() throws EncodeException, DecodeException {
		var getPeersMessage1 = GetPeerInfosMessages.of("id");
		String encoded = new GetPeerInfosMessages.Encoder().encode(getPeersMessage1);
		var getPeersMessage2 = new GetPeerInfosMessages.Decoder().decode(encoded);
		assertEquals(getPeersMessage1, getPeersMessage2);
	}

	@Test
	@DisplayName("getPeersResult messages are correctly encoded into Json and decoded from Json")
	public void encodeDecodeWorksForGetPeersResult() throws EncodeException, DecodeException, URISyntaxException {
		var peerInfo1 = PeerInfos.of(Peers.of(new URI("ws://google.com:8011")), 1234, true);
		var peerInfo2 = PeerInfos.of(Peers.of(new URI("ws://amazon.it:8024")), 313, false);
		var peerInfo3 = PeerInfos.of(Peers.of(new URI("ws://panarea.io:8025")), 112, true);
		var getPeersResultMessage1 = GetPeerInfosResultMessages.of(Stream.of(peerInfo1, peerInfo2, peerInfo3), "id");
		String encoded = new GetPeerInfosResultMessages.Encoder().encode(getPeersResultMessage1);
		var getPeersResultMessage2 = new GetPeerInfosResultMessages.Decoder().decode(encoded);
		assertEquals(getPeersResultMessage1, getPeersResultMessage2);
	}

	@Test
	@DisplayName("getBlock messages are correctly encoded into Json and decoded from Json")
	public void encodeDecodeWorksForGetBlock() throws EncodeException, DecodeException {
		var getBlockMessage1 = GetBlockMessages.of(new byte[] { 1, 2, 3, 4, 5 }, "id");
		String encoded = new GetBlockMessages.Encoder().encode(getBlockMessage1);
		var getBlockMessage2 = new GetBlockMessages.Decoder().decode(encoded);
		assertEquals(getBlockMessage1, getBlockMessage2);
	}

	@Test
	@DisplayName("non-empty getBlockResult messages are correctly encoded into Json and decoded from Json")
	public void encodeDecodeWorksForGetBlockResultNonEmpty() throws EncodeException, DecodeException, NoSuchAlgorithmException, InvalidKeyException, SignatureException {
		var hashing = HashingAlgorithms.shabal256();
		var value = new byte[hashing.length()];
		for (int pos = 0; pos < value.length; pos++)
			value[pos] = (byte) pos;
		var ed25519 = SignatureAlgorithms.ed25519();
		var nodeKeyPair = ed25519.getKeyPair();
		var plotKeyPair = ed25519.getKeyPair();
		var prolog = Prologs.of("octopus", ed25519, nodeKeyPair.getPublic(), ed25519, plotKeyPair.getPublic(), new byte[0]);
		var deadline = Deadlines.of(prolog, 13, value, 11, new byte[] { 90, 91, 92 }, hashing, plotKeyPair.getPrivate());
		var block = Blocks.of(13, BigInteger.TEN, 1234L, 1100L, BigInteger.valueOf(13011973), deadline, new byte[] { 1, 2, 3, 4, 5, 6}, nodeKeyPair.getPrivate());
		var getBlockResultMessage1 = GetBlockResultMessages.of(Optional.of(block), "id");
		String encoded = new GetBlockResultMessages.Encoder().encode(getBlockResultMessage1);
		var getBlockResultMessage2 = new GetBlockResultMessages.Decoder().decode(encoded);
		assertEquals(getBlockResultMessage1, getBlockResultMessage2);
	}

	@Test
	@DisplayName("empty getBlockResult messages are correctly encoded into Json and decoded from Json")
	public void encodeDecodeWorksForGetBlockResultEmpty() throws EncodeException, DecodeException {
		var getBlockResultMessage1 = GetBlockResultMessages.of(Optional.empty(), "id");
		String encoded = new GetBlockResultMessages.Encoder().encode(getBlockResultMessage1);
		var getBlockResultMessage2 = new GetBlockResultMessages.Decoder().decode(encoded);
		assertEquals(getBlockResultMessage1, getBlockResultMessage2);
	}

	@Test
	@DisplayName("getBlockDescription messages are correctly encoded into Json and decoded from Json")
	public void encodeDecodeWorksForGetBlockDescription() throws EncodeException, DecodeException {
		var getBlockDescriptionMessage1 = GetBlockDescriptionMessages.of(new byte[] { 1, 2, 3, 4, 5 }, "id");
		String encoded = new GetBlockDescriptionMessages.Encoder().encode(getBlockDescriptionMessage1);
		var getBlockDescriptionMessage2 = new GetBlockDescriptionMessages.Decoder().decode(encoded);
		assertEquals(getBlockDescriptionMessage1, getBlockDescriptionMessage2);
	}

	@Test
	@DisplayName("non-empty getBlockDescriptionResult messages are correctly encoded into Json and decoded from Json")
	public void encodeDecodeWorksForGetBlockDescriptionResultNonEmpty() throws EncodeException, DecodeException, NoSuchAlgorithmException, InvalidKeyException, SignatureException {
		var hashing = HashingAlgorithms.shabal256();
		var value = new byte[hashing.length()];
		for (int pos = 0; pos < value.length; pos++)
			value[pos] = (byte) pos;
		var ed25519 = SignatureAlgorithms.ed25519();
		var nodeKeyPair = ed25519.getKeyPair();
		var plotKeyPair = ed25519.getKeyPair();
		var prolog = Prologs.of("octopus", ed25519, nodeKeyPair.getPublic(), ed25519, plotKeyPair.getPublic(), new byte[0]);
		var deadline = Deadlines.of(prolog, 13, value, 11, new byte[] { 90, 91, 92 }, hashing, plotKeyPair.getPrivate());
		var block = BlockDescriptions.of(13, BigInteger.TEN, 1234L, 1100L, BigInteger.valueOf(13011973), deadline, new byte[] { 1, 2, 3, 4, 5, 6});
		var getBlockDescriptionResultMessage1 = GetBlockDescriptionResultMessages.of(Optional.of(block), "id");
		String encoded = new GetBlockDescriptionResultMessages.Encoder().encode(getBlockDescriptionResultMessage1);
		var getBlockDescriptionResultMessage2 = new GetBlockDescriptionResultMessages.Decoder().decode(encoded);
		assertEquals(getBlockDescriptionResultMessage1, getBlockDescriptionResultMessage2);
	}

	@Test
	@DisplayName("empty getBlockDescriptionResult messages are correctly encoded into Json and decoded from Json")
	public void encodeDecodeWorksForGetBlockDescriptionResultEmpty() throws EncodeException, DecodeException {
		var getBlockDescriptionResultMessage1 = GetBlockDescriptionResultMessages.of(Optional.empty(), "id");
		String encoded = new GetBlockDescriptionResultMessages.Encoder().encode(getBlockDescriptionResultMessage1);
		var getBlockDescriptionResultMessage2 = new GetBlockDescriptionResultMessages.Decoder().decode(encoded);
		assertEquals(getBlockDescriptionResultMessage1, getBlockDescriptionResultMessage2);
	}

	@Test
	@DisplayName("getChainPortion messages are correctly encoded into Json and decoded from Json")
	public void encodeDecodeWorksForGetChainPortion() throws EncodeException, DecodeException {
		var getChainPortionMessage1 = GetChainPortionMessages.of(13, 20, "id");
		String encoded = new GetChainPortionMessages.Encoder().encode(getChainPortionMessage1);
		var getChainPortionMessage2 = new GetChainPortionMessages.Decoder().decode(encoded);
		assertEquals(getChainPortionMessage1, getChainPortionMessage2);
	}

	@Test
	@DisplayName("getPeerInfos messages are correctly encoded into Json and decoded from Json")
	public void encodeDecodeWorksForGetPeerInfos() throws EncodeException, DecodeException {
		var getPeerInfosMessage1 = GetPeerInfosMessages.of("id");
		String encoded = new GetPeerInfosMessages.Encoder().encode(getPeerInfosMessage1);
		var getPeerInfosMessage2 = new GetPeerInfosMessages.Decoder().decode(encoded);
		assertEquals(getPeerInfosMessage1, getPeerInfosMessage2);
	}

	@Test
	@DisplayName("getPeerInfosResult messages are correctly encoded into Json and decoded from Json")
	public void encodeDecodeWorksForGetPeerInfosResult() throws EncodeException, DecodeException, URISyntaxException {
		var peers = Stream.of(PeerInfos.of(Peers.of(new URI("ws://www.hotmoka.io")), 100L, true),
				PeerInfos.of(Peers.of(new URI("ws://www.mokamint.io:8030")), 123L, false));
		var getPeerInfosResultMessage1 = GetPeerInfosResultMessages.of(peers, "id");
		String encoded = new GetPeerInfosResultMessages.Encoder().encode(getPeerInfosResultMessage1);
		var getPeerInfosResultMessage2 = new GetPeerInfosResultMessages.Decoder().decode(encoded);
		assertEquals(getPeerInfosResultMessage1, getPeerInfosResultMessage2);
	}

	@Test
	@DisplayName("getMinerInfos messages are correctly encoded into Json and decoded from Json")
	public void encodeDecodeWorksForGetMinerInfos() throws EncodeException, DecodeException {
		var getMinerInfosMessage1 = GetMinerInfosMessages.of("id");
		String encoded = new GetMinerInfosMessages.Encoder().encode(getMinerInfosMessage1);
		var getMinerInfosMessage2 = new GetMinerInfosMessages.Decoder().decode(encoded);
		assertEquals(getMinerInfosMessage1, getMinerInfosMessage2);
	}

	@Test
	@DisplayName("getMinerInfosResult messages are correctly encoded into Json and decoded from Json")
	public void encodeDecodeWorksForGetMinerInfosResult() throws EncodeException, DecodeException, URISyntaxException {
		var miners = Stream.of(MinerInfos.of(UUID.randomUUID(), 100L, "a miner"),
				MinerInfos.of(UUID.randomUUID(), 123L, "another miner"));
		var getMinerInfosResultMessage1 = GetMinerInfosResultMessages.of(miners, "id");
		String encoded = new GetMinerInfosResultMessages.Encoder().encode(getMinerInfosResultMessage1);
		var getMinerInfosResultMessage2 = new GetMinerInfosResultMessages.Decoder().decode(encoded);
		assertEquals(getMinerInfosResultMessage1, getMinerInfosResultMessage2);
	}

	@Test
	@DisplayName("getTaskInfos messages are correctly encoded into Json and decoded from Json")
	public void encodeDecodeWorksForGetTaskInfos() throws EncodeException, DecodeException {
		var getTaskInfosMessage1 = GetTaskInfosMessages.of("id");
		String encoded = new GetTaskInfosMessages.Encoder().encode(getTaskInfosMessage1);
		var getTaskInfosMessage2 = new GetTaskInfosMessages.Decoder().decode(encoded);
		assertEquals(getTaskInfosMessage1, getTaskInfosMessage2);
	}

	@Test
	@DisplayName("getTaskInfosResult messages are correctly encoded into Json and decoded from Json")
	public void encodeDecodeWorksForGetTaskInfosResult() throws EncodeException, DecodeException, URISyntaxException {
		var tasks = Stream.of(TaskInfos.of("a beautiful task"), TaskInfos.of("another beautiful task"));
		var getTaskInfosResultMessage1 = GetTaskInfosResultMessages.of(tasks, "id");
		String encoded = new GetTaskInfosResultMessages.Encoder().encode(getTaskInfosResultMessage1);
		var getTaskInfosResultMessage2 = new GetTaskInfosResultMessages.Decoder().decode(encoded);
		assertEquals(getTaskInfosResultMessage1, getTaskInfosResultMessage2);
	}

	@Test
	@DisplayName("getChainResult messages are correctly encoded into Json and decoded from Json")
	public void encodeDecodeWorksForGetChainResult() throws EncodeException, DecodeException {
		var chain = ChainPortions.of(Stream.of(new byte[] { 1, 2, 3 }, new byte[] { 20, 50, 70, 88 }));
		var getChainResultMessage1 = GetChainPortionResultMessages.of(chain, "id");
		String encoded = new GetChainPortionResultMessages.Encoder().encode(getChainResultMessage1);
		var getChainResultMessage2 = new GetChainPortionResultMessages.Decoder().decode(encoded);
		assertEquals(getChainResultMessage1, getChainResultMessage2);
	}

	@Test
	@DisplayName("getConfig messages are correctly encoded into Json and decoded from Json")
	public void encodeDecodeWorksForGetConfig() throws EncodeException, DecodeException {
		var getConfigMessage1 = GetConfigMessages.of("id");
		String encoded = new GetConfigMessages.Encoder().encode(getConfigMessage1);
		var getConfigMessage2 = new GetConfigMessages.Decoder().decode(encoded);
		assertEquals(getConfigMessage1, getConfigMessage2);
	}

	@Test
	@DisplayName("getInfo messages are correctly encoded into Json and decoded from Json")
	public void encodeDecodeWorksForGetInfo() throws EncodeException, DecodeException {
		var getInfoMessage1 = GetInfoMessages.of("id");
		String encoded = new GetInfoMessages.Encoder().encode(getInfoMessage1);
		var getInfoMessage2 = new GetInfoMessages.Decoder().decode(encoded);
		assertEquals(getInfoMessage1, getInfoMessage2);
	}

	@Test
	@DisplayName("getConfigResult messages are correctly encoded into Json and decoded from Json")
	public void encodeDecodeWorksForGetConfigResult() throws EncodeException, DecodeException, NoSuchAlgorithmException {
		var config = ConsensusConfigBuilders.defaults().build();
		var getConfigResultMessage1 = GetConfigResultMessages.of(config, "id");
		String encoded = new GetConfigResultMessages.Encoder().encode(getConfigResultMessage1);
		var getConfigResultMessage2 = new GetConfigResultMessages.Decoder().decode(encoded);
		assertEquals(getConfigResultMessage1, getConfigResultMessage2);
	}

	@Test
	@DisplayName("exception result messages are correctly encoded into Json and decoded from Json")
	public void encodeDecodeWorksForExceptionResult() throws EncodeException, DecodeException {
		var exceptionResultMessage1 = ExceptionMessages.of(NoSuchAlgorithmException.class, "something went wrong", "id");
		String encoded = new ExceptionMessages.Encoder().encode(exceptionResultMessage1);
		var exceptionResultMessage2 = new ExceptionMessages.Decoder().decode(encoded);
		assertEquals(exceptionResultMessage1, exceptionResultMessage2);
	}

	@Test
	@DisplayName("getChainInfo messages are correctly encoded into Json and decoded from Json")
	public void encodeDecodeWorksForGetChainInfo() throws EncodeException, DecodeException {
		var getChainInfo1 = GetChainInfoMessages.of("id");
		String encoded = new GetChainInfoMessages.Encoder().encode(getChainInfo1);
		var getChainInfo2 = new GetChainInfoMessages.Decoder().decode(encoded);
		assertEquals(getChainInfo1, getChainInfo2);
	}

	@Test
	@DisplayName("getChainInfoResult messages are correctly encoded into Json and decoded from Json")
	public void encodeDecodeWorksForGetChainInfoResult() throws EncodeException, DecodeException {
		var info = ChainInfos.of(1973L, Optional.of(new byte[] { 1, 2, 3, 4 }), Optional.of(new byte[] { 3, 7, 8, 11 }));
		var getChainInfoResultMessage1 = GetChainInfoResultMessages.of(info, "id");
		String encoded = new GetChainInfoResultMessages.Encoder().encode(getChainInfoResultMessage1);
		var getChainInfoResultMessage2 = new GetChainInfoResultMessages.Decoder().decode(encoded);
		assertEquals(getChainInfoResultMessage1, getChainInfoResultMessage2);
	}

	@Test
	@DisplayName("getInfoResult messages are correctly encoded into Json and decoded from Json")
	public void encodeDecodeWorksForGetInfoResult() throws EncodeException, DecodeException {
		var info = NodeInfos.of(Versions.of(3, 4, 5), UUID.randomUUID(), LocalDateTime.now(ZoneId.of("UTC")));
		var getInfoResultMessage1 = GetInfoResultMessages.of(info, "id");
		String encoded = new GetInfoResultMessages.Encoder().encode(getInfoResultMessage1);
		var getInfoResultMessage2 = new GetInfoResultMessages.Decoder().decode(encoded);
		assertEquals(getInfoResultMessage1, getInfoResultMessage2);
	}

	@Test
	@DisplayName("post transaction messages are correctly encoded into Json and decoded from Json")
	public void encodeDecodeWorksForPostTransaction() throws EncodeException, DecodeException {
		var transaction = Transactions.of(new byte[] { 1, 2, 3, 4, 5 });
		var postTransactionMessage1 = AddTransactionMessages.of(transaction, "id");
		String encoded = new AddTransactionMessages.Encoder().encode(postTransactionMessage1);
		var postTransactionMessage2 = new AddTransactionMessages.Decoder().decode(encoded);
		assertEquals(postTransactionMessage1, postTransactionMessage2);
	}

	@Test
	@DisplayName("post transaction result messages are correctly encoded into Json and decoded from Json")
	public void encodeDecodeWorksForPostTransactionResult() throws EncodeException, DecodeException {
		var postTransactionResultMessage1 = AddTransactionResultMessages.of(true, "id");
		String encoded = new AddTransactionResultMessages.Encoder().encode(postTransactionResultMessage1);
		var postTransactionResultMessage2 = new AddTransactionResultMessages.Decoder().decode(encoded);
		assertEquals(postTransactionResultMessage1, postTransactionResultMessage2);
	}

	@Test
	@DisplayName("addPeers messages are correctly encoded into Json and decoded from Json")
	public void encodeDecodeWorksForAddPeers() throws EncodeException, DecodeException, URISyntaxException {
		var addPeers1 = AddPeerMessages.of(Peers.of(new URI("ws://google.com:8011")), "id");
		String encoded = new AddPeerMessages.Encoder().encode(addPeers1);
		var addPeers2 = new AddPeerMessages.Decoder().decode(encoded);
		assertEquals(addPeers1, addPeers2);
	}

	@Test
	@DisplayName("removePeer messages are correctly encoded into Json and decoded from Json")
	public void encodeDecodeWorksForRemovePeer() throws EncodeException, DecodeException, URISyntaxException {
		var removePeers1 = RemovePeerMessages.of(Peers.of(new URI("ws://google.com:8011")), "id");
		String encoded = new RemovePeerMessages.Encoder().encode(removePeers1);
		var removePeers2 = new RemovePeerMessages.Decoder().decode(encoded);
		assertEquals(removePeers1, removePeers2);
	}

	@Test
	@DisplayName("addPeer non-empty result messages are correctly encoded into Json and decoded from Json")
	public void encodeDecodeWorksForNonEmptyAddPeerResult() throws EncodeException, DecodeException, URISyntaxException {
		var addPeerResultMessage1 = AddPeerResultMessages.of(Optional.of(PeerInfos.of(Peers.of(new URI("ws://www.mokamint-io")), 800, true)), "id");
		String encoded = new AddPeerResultMessages.Encoder().encode(addPeerResultMessage1);
		var addPeerResultMessage2 = new AddPeerResultMessages.Decoder().decode(encoded);
		assertEquals(addPeerResultMessage1, addPeerResultMessage2);
	}

	@Test
	@DisplayName("addPeer empty result messages are correctly encoded into Json and decoded from Json")
	public void encodeDecodeWorksForEmptyAddPeerResult() throws EncodeException, DecodeException, URISyntaxException {
		var addPeerResultMessage1 = AddPeerResultMessages.of(Optional.empty(), "id");
		String encoded = new AddPeerResultMessages.Encoder().encode(addPeerResultMessage1);
		var addPeerResultMessage2 = new AddPeerResultMessages.Decoder().decode(encoded);
		assertEquals(addPeerResultMessage1, addPeerResultMessage2);
	}

	@Test
	@DisplayName("removePeer result messages are correctly encoded into Json and decoded from Json")
	public void encodeDecodeWorksForRemovePeerResult() throws EncodeException, DecodeException {
		var removePeerResultMessage1 = RemovePeerResultMessages.of(true, "id");
		String encoded = new RemovePeerResultMessages.Encoder().encode(removePeerResultMessage1);
		var removePeerResultMessage2 = new RemovePeerResultMessages.Decoder().decode(encoded);
		assertEquals(removePeerResultMessage1, removePeerResultMessage2);
	}

	@Test
	@DisplayName("openMiner messages are correctly encoded into Json and decoded from Json")
	public void encodeDecodeWorksForOpenMiner() throws EncodeException, DecodeException {
		var openMiner1 = OpenMinerMessages.of(8025, "id");
		String encoded = new OpenMinerMessages.Encoder().encode(openMiner1);
		var openMiner2 = new OpenMinerMessages.Decoder().decode(encoded);
		assertEquals(openMiner1, openMiner2);
	}

	@Test
	@DisplayName("openMiner result messages are correctly encoded into Json and decoded from Json")
	public void encodeDecodeWorksForOpenMinerResult() throws EncodeException, DecodeException {
		var openMinerResultMessage1 = OpenMinerResultMessages.of(true, "id");
		String encoded = new OpenMinerResultMessages.Encoder().encode(openMinerResultMessage1);
		var openMinerResultMessage2 = new OpenMinerResultMessages.Decoder().decode(encoded);
		assertEquals(openMinerResultMessage1, openMinerResultMessage2);
	}

	@Test
	@DisplayName("closeMiner messages are correctly encoded into Json and decoded from Json")
	public void encodeDecodeWorksForCloseMiner() throws EncodeException, DecodeException {
		var closeMiner1 = CloseMinerMessages.of(UUID.randomUUID(), "id");
		String encoded = new CloseMinerMessages.Encoder().encode(closeMiner1);
		var closeMiner2 = new CloseMinerMessages.Decoder().decode(encoded);
		assertEquals(closeMiner1, closeMiner2);
	}

	@Test
	@DisplayName("closeMiner result messages are correctly encoded into Json and decoded from Json")
	public void encodeDecodeWorksForCloseMinerResult() throws EncodeException, DecodeException {
		var closeMinerResultMessage1 = CloseMinerResultMessages.of(true, "id");
		String encoded = new CloseMinerResultMessages.Encoder().encode(closeMinerResultMessage1);
		var closeMinerResultMessage2 = new CloseMinerResultMessages.Decoder().decode(encoded);
		assertEquals(closeMinerResultMessage1, closeMinerResultMessage2);
	}

	@Test
	@DisplayName("whisperPeers messages are correctly encoded into Json and decoded from Json")
	public void encodeDecodeWorksForWhisperPeers() throws EncodeException, DecodeException, URISyntaxException {
		var peer1 = Peers.of(new URI("ws://google.com:8011"));
		var peer2 = Peers.of(new URI("ws://amazon.it:8024"));
		var peer3 = Peers.of(new URI("ws://panarea.io:8025"));
		var whisperPeersMessage1 = WhisperPeersMessages.of(Stream.of(peer1, peer2, peer3), "id");
		String encoded = new WhisperPeersMessages.Encoder().encode(whisperPeersMessage1);
		var whisperPeersMessage2 = new WhisperPeersMessages.Decoder().decode(encoded);
		assertEquals(whisperPeersMessage1, whisperPeersMessage2);
	}

	@Test
	@DisplayName("whisperBlock messages are correctly encoded into Json and decoded from Json")
	public void encodeDecodeWorksForWhisperBlock() throws EncodeException, DecodeException, URISyntaxException, NoSuchAlgorithmException, InvalidKeyException, SignatureException {
		HashingAlgorithm hashing = HashingAlgorithms.shabal256();
		var value = new byte[hashing.length()];
		for (int pos = 0; pos < value.length; pos++)
			value[pos] = (byte) pos;
		var ed25519 = SignatureAlgorithms.ed25519();
		var nodeKeyPair = ed25519.getKeyPair();
		var plotKeyPair = ed25519.getKeyPair();
		var prolog = Prologs.of("octopus", ed25519, nodeKeyPair.getPublic(), ed25519, plotKeyPair.getPublic(), new byte[0]);
		var deadline = Deadlines.of(prolog, 13, value, 11, new byte[] { 90, 91, 92 }, hashing, plotKeyPair.getPrivate());
		var block = Blocks.of(13, BigInteger.TEN, 1234L, 1100L, BigInteger.valueOf(13011973), deadline, new byte[] { 1, 2, 3, 4, 5, 6}, nodeKeyPair.getPrivate());
		var whisperBlockMessage1 = WhisperBlockMessages.of(block, "id");
		String encoded = new WhisperBlockMessages.Encoder().encode(whisperBlockMessage1);
		var whisperBlockMessage2 = new WhisperBlockMessages.Decoder().decode(encoded);
		assertEquals(whisperBlockMessage1, whisperBlockMessage2);
	}

	@Test
	@DisplayName("exception result messages cannot be decoded from Json if the class type is not an exception")
	public void decodeFailsForExceptionResultIfNotException() {
		String encoded = "{\"clazz\":\"java.lang.String\",\"message\":\"something went wrong\", \"type\":\"" + ExceptionMessage.class.getName() + "\",\"id\":\"id\"}";
		DecodeException e = assertThrows(DecodeException.class, () -> new ExceptionMessages.Decoder().decode(encoded));
		assertTrue(e.getCause() instanceof ClassCastException);
	}
}