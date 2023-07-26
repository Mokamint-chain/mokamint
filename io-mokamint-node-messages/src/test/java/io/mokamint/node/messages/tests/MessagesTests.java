package io.mokamint.node.messages.tests;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.fail;

import java.io.IOException;
import java.math.BigInteger;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.security.NoSuchAlgorithmException;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.logging.LogManager;
import java.util.stream.Stream;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import io.hotmoka.crypto.HashingAlgorithms;
import io.mokamint.node.Blocks;
import io.mokamint.node.ChainInfos;
import io.mokamint.node.ConsensusConfigs;
import io.mokamint.node.NodeInfos;
import io.mokamint.node.PeerInfos;
import io.mokamint.node.Peers;
import io.mokamint.node.Versions;
import io.mokamint.node.messages.AddPeerMessages;
import io.mokamint.node.messages.AddPeerResultMessages;
import io.mokamint.node.messages.ExceptionMessages;
import io.mokamint.node.messages.GetBlockMessages;
import io.mokamint.node.messages.GetBlockResultMessages;
import io.mokamint.node.messages.GetChainInfoMessages;
import io.mokamint.node.messages.GetChainInfoResultMessages;
import io.mokamint.node.messages.GetConfigMessages;
import io.mokamint.node.messages.GetConfigResultMessages;
import io.mokamint.node.messages.GetInfoMessages;
import io.mokamint.node.messages.GetInfoResultMessages;
import io.mokamint.node.messages.GetPeerInfosMessages;
import io.mokamint.node.messages.GetPeerInfosResultMessages;
import io.mokamint.node.messages.RemovePeerMessages;
import io.mokamint.node.messages.RemovePeerResultMessages;
import io.mokamint.node.messages.WhisperPeersMessages;
import io.mokamint.node.messages.api.ExceptionMessage;
import io.mokamint.nonce.Deadlines;
import jakarta.websocket.DecodeException;
import jakarta.websocket.EncodeException;

public class MessagesTests {

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
	public void encodeDecodeWorksForGetBlockResultNonEmpty() throws EncodeException, DecodeException {
		var hashing = HashingAlgorithms.shabal256(Function.identity());
		var deadline = Deadlines.of(new byte[] {80, 81, 83}, 13, new byte[] { 4, 5, 6 }, 11, new byte[] { 90, 91, 92 }, hashing);
		var block = Blocks.of(13, BigInteger.TEN, 1234L, 1100L, BigInteger.valueOf(13011973), deadline, new byte[] { 1, 2, 3, 4, 5, 6});
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
		var config = ConsensusConfigs.defaults().build();
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
		var info = NodeInfos.of(Versions.of(3, 4, 5), UUID.randomUUID());
		var getInfoResultMessage1 = GetInfoResultMessages.of(info, "id");
		String encoded = new GetInfoResultMessages.Encoder().encode(getInfoResultMessage1);
		var getInfoResultMessage2 = new GetInfoResultMessages.Decoder().decode(encoded);
		assertEquals(getInfoResultMessage1, getInfoResultMessage2);
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
	@DisplayName("addPeer result messages are correctly encoded into Json and decoded from Json")
	public void encodeDecodeWorksForAddPeerResult() throws EncodeException, DecodeException {
		var addPeerResultMessage1 = AddPeerResultMessages.of("id");
		String encoded = new AddPeerResultMessages.Encoder().encode(addPeerResultMessage1);
		var addPeerResultMessage2 = new AddPeerResultMessages.Decoder().decode(encoded);
		assertEquals(addPeerResultMessage1, addPeerResultMessage2);
	}

	@Test
	@DisplayName("removePeer result messages are correctly encoded into Json and decoded from Json")
	public void encodeDecodeWorksForRemovePeerResult() throws EncodeException, DecodeException {
		var removePeerResultMessage1 = RemovePeerResultMessages.of("id");
		String encoded = new RemovePeerResultMessages.Encoder().encode(removePeerResultMessage1);
		var removePeerResultMessage2 = new RemovePeerResultMessages.Decoder().decode(encoded);
		assertEquals(removePeerResultMessage1, removePeerResultMessage2);
	}

	@Test
	@DisplayName("suggestPeers messages are correctly encoded into Json and decoded from Json")
	public void encodeDecodeWorksForSuggestPeers() throws EncodeException, DecodeException, URISyntaxException {
		var peer1 = Peers.of(new URI("ws://google.com:8011"));
		var peer2 = Peers.of(new URI("ws://amazon.it:8024"));
		var peer3 = Peers.of(new URI("ws://panarea.io:8025"));
		var suggestPeersMessage1 = WhisperPeersMessages.of(Stream.of(peer1, peer2, peer3), "id");
		String encoded = new WhisperPeersMessages.Encoder().encode(suggestPeersMessage1);
		var suggestPeersMessage2 = new WhisperPeersMessages.Decoder().decode(encoded);
		assertEquals(suggestPeersMessage1, suggestPeersMessage2);
	}

	@Test
	@DisplayName("exception result messages cannot be decoded from Json if the class type is not an exception")
	public void decodeFailsForExceptionResultIfNotException() {
		String encoded = "{\"clazz\":\"java.lang.String\",\"message\":\"something went wrong\", \"type\":\"" + ExceptionMessage.class.getName() + "\",\"id\":\"id\"}";

		try {
			new ExceptionMessages.Decoder().decode(encoded);
		}
		catch (DecodeException e) {
			if (e.getCause() instanceof ClassCastException)
				return;
		}

		fail();
	}

	static {
		String current = System.getProperty("java.util.logging.config.file");
		if (current == null) {
			// if the property is not set, we provide a default (if it exists)
			URL resource = MessagesTests.class.getClassLoader().getResource("logging.properties");
			if (resource != null)
				try (var is = resource.openStream()) {
					LogManager.getLogManager().readConfiguration(is);
				}
				catch (SecurityException | IOException e) {
					throw new RuntimeException("Cannot load logging.properties file", e);
				}
		}
	}
}