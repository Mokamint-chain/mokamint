package io.mokamint.nonce.tests;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.IOException;
import java.net.URL;
import java.util.function.Function;
import java.util.logging.LogManager;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import io.hotmoka.crypto.HashingAlgorithms;
import io.mokamint.nonce.Deadlines;
import jakarta.websocket.DecodeException;
import jakarta.websocket.EncodeException;

public class DeadlinesTests {

	@Test
	@DisplayName("deadlines are correctly encoded into Json and decoded from Json")
	public void encodeDecodeWorksForDeadlines() throws EncodeException, DecodeException {
		var hashing = HashingAlgorithms.shabal256(Function.identity());
		var deadline1 = Deadlines.of(new byte[] {80, 81, 83}, 13, new byte[] { 4, 5, 6 }, 11, new byte[] { 90, 91, 92 }, hashing);
		String encoded = new Deadlines.Encoder().encode(deadline1);
		var deadline2 = new Deadlines.Decoder().decode(encoded);
		assertEquals(deadline1, deadline2);
	}

	static {
		String current = System.getProperty("java.util.logging.config.file");
		if (current == null) {
			// if the property is not set, we provide a default (if it exists)
			URL resource = DeadlinesTests.class.getClassLoader().getResource("logging.properties");
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