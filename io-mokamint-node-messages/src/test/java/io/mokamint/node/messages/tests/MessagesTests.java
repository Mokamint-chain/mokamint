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
import java.util.function.Function;
import java.util.logging.LogManager;
import java.util.stream.Stream;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import io.hotmoka.crypto.HashingAlgorithms;
import io.mokamint.node.Blocks;
import io.mokamint.node.Peers;
import io.mokamint.node.messages.ExceptionResultMessage;
import io.mokamint.node.messages.ExceptionResultMessages;
import io.mokamint.node.messages.GetBlockMessages;
import io.mokamint.node.messages.GetBlockResultMessages;
import io.mokamint.node.messages.GetPeersMessages;
import io.mokamint.node.messages.GetPeersResultMessages;
import io.mokamint.nonce.Deadlines;
import jakarta.websocket.DecodeException;
import jakarta.websocket.EncodeException;

public class MessagesTests {

	@Test
	@DisplayName("getPeers messages are correctly encoded into Json and decoded from Json")
	public void encodeDecodeWorksForGetPeers() throws EncodeException, DecodeException {
		var getPeersMessage1 = GetPeersMessages.of("id");
		String encoded = new GetPeersMessages.Encoder().encode(getPeersMessage1);
		var getPeersMessage2 = new GetPeersMessages.Decoder().decode(encoded);
		assertEquals(getPeersMessage1, getPeersMessage2);
	}

	@Test
	@DisplayName("getPeersResult messages are correctly encoded into Json and decoded from Json")
	public void encodeDecodeWorksForGetPeersResult() throws EncodeException, DecodeException, URISyntaxException {
		var peer1 = Peers.of(new URI("ws://google.com:8011"));
		var peer2 = Peers.of(new URI("ws://amazon.it:8024"));
		var peer3 = Peers.of(new URI("ws://panarea.io:8025"));
		var getPeersResultMessage1 = GetPeersResultMessages.of(Stream.of(peer1, peer2, peer3), "id");
		String encoded = new GetPeersResultMessages.Encoder().encode(getPeersResultMessage1);
		var getPeersResultMessage2 = new GetPeersResultMessages.Decoder().decode(encoded);
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
		var block = Blocks.of(13, 1234L, 1100L, BigInteger.valueOf(13011973), deadline, new byte[] { 1, 2, 3, 4, 5, 6});
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
	@DisplayName("exception result messages are correctly encoded into Json and decoded from Json")
	public void encodeDecodeWorksForExceptionResult() throws EncodeException, DecodeException {
		var exceptionResultMessage1 = ExceptionResultMessages.of(NoSuchAlgorithmException.class, "something went wrong", "id");
		String encoded = new ExceptionResultMessages.Encoder().encode(exceptionResultMessage1);
		var exceptionResultMessage2 = new ExceptionResultMessages.Decoder().decode(encoded);
		assertEquals(exceptionResultMessage1, exceptionResultMessage2);
	}

	@Test
	@DisplayName("exception result messages cannot be decoded from Json if the class type is not an exception")
	public void decodeFailsForExceptionResultIfNotException() {
		String encoded = "{\"clazz\":\"java.lang.String\",\"message\":\"something went wrong\", \"type\":\"" + ExceptionResultMessage.class.getName() + "\",\"id\":\"id\"}";

		try {
			new ExceptionResultMessages.Decoder().decode(encoded);
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