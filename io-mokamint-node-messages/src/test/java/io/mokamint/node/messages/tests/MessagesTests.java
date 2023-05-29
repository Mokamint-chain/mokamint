package io.mokamint.node.messages.tests;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.IOException;
import java.net.URL;
import java.util.logging.LogManager;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import io.mokamint.node.messages.GetBlockMessages;
import io.mokamint.node.messages.GetPeersMessages;
import jakarta.websocket.DecodeException;
import jakarta.websocket.EncodeException;

public class MessagesTests {

	@Test
	@DisplayName("getPeers messages are correctly encoded into Json and decoded from Json")
	public void encodeDecodeWorksForGetPeers() throws EncodeException, DecodeException {
		var getPeersMessage1 = GetPeersMessages.instance();
		String encoded = new GetPeersMessages.Encoder().encode(getPeersMessage1);
		var getPeersMessage2 = new GetPeersMessages.Decoder().decode(encoded);
		assertEquals(getPeersMessage1, getPeersMessage2);
	}

	@Test
	@DisplayName("getBlock messages are correctly encoded into Json and decoded from Json")
	public void encodeDecodeWorksForGetBlock() throws EncodeException, DecodeException {
		var getBlockMessage1 = GetBlockMessages.of(new byte[] { 1, 2, 3, 4, 5 });
		String encoded = new GetBlockMessages.Encoder().encode(getBlockMessage1);
		var getBlockMessage2 = new GetBlockMessages.Decoder().decode(encoded);
		assertEquals(getBlockMessage1, getBlockMessage2);
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