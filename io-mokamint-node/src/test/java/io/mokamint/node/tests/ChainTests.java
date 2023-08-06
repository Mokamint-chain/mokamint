package io.mokamint.node.tests;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.IOException;
import java.net.URL;
import java.util.logging.LogManager;
import java.util.stream.Stream;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import io.mokamint.node.Chains;
import jakarta.websocket.DecodeException;
import jakarta.websocket.EncodeException;

public class ChainTests {

	@Test
	@DisplayName("chains with hashes are correctly encoded into Json and decoded from Json")
	public void encodeDecodeWorksWithHashes() throws EncodeException, DecodeException {
		var hash1 = new byte[] { 1, 2, 4, 100, 12 };
		var hash2 = new byte[] { 13, 20, 4, 99, 12, 11 };
		var chain1 = Chains.of(Stream.of(hash1, hash2));
		String encoded = new Chains.Encoder().encode(chain1);
		var chain2 = new Chains.Decoder().decode(encoded);
		assertEquals(chain1, chain2);
	}

	@Test
	@DisplayName("chains with no hashes are correctly encoded into Json and decoded from Json")
	public void encodeDecodeWorksWithoutHashes() throws EncodeException, DecodeException {
		var chain1 = Chains.of(Stream.empty());
		String encoded = new Chains.Encoder().encode(chain1);
		var chain2 = new Chains.Decoder().decode(encoded);
		assertEquals(chain1, chain2);
	}

	static {
		String current = System.getProperty("java.util.logging.config.file");
		if (current == null) {
			// if the property is not set, we provide a default (if it exists)
			URL resource = ChainTests.class.getClassLoader().getResource("logging.properties");
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