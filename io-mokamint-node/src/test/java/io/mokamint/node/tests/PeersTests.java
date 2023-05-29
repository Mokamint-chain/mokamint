package io.mokamint.node.tests;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.logging.LogManager;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import io.mokamint.node.Peers;
import jakarta.websocket.DecodeException;
import jakarta.websocket.EncodeException;

public class PeersTests {

	@Test
	@DisplayName("peers are correctly encoded into Json and decoded from Json")
	public void encodeDecodeWorks() throws EncodeException, URISyntaxException, DecodeException {
		var peer1 = Peers.of(new URI("ws://mygreatsite.org:2090"));
		String encoded = new Peers.Encoder().encode(peer1);
		var peer2 = new Peers.Decoder().decode(encoded);
		assertEquals(peer1, peer2);
	}

	static {
		String current = System.getProperty("java.util.logging.config.file");
		if (current == null) {
			// if the property is not set, we provide a default (if it exists)
			URL resource = PeersTests.class.getClassLoader().getResource("logging.properties");
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