package io.mokamint.nonce.tests;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.IOException;
import java.net.URL;
import java.util.function.Function;
import java.util.logging.LogManager;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import io.hotmoka.crypto.HashingAlgorithms;
import io.mokamint.nonce.DeadlineDescriptions;
import jakarta.websocket.DecodeException;
import jakarta.websocket.EncodeException;

public class DeadlineDescriptionsTests {

	@Test
	@DisplayName("deadline descriptions are correctly encoded into Json and decoded from Json")
	public void encodeDecodeWorksForDeadlineDescriptions() throws EncodeException, DecodeException {
		var hashing = HashingAlgorithms.shabal256(Function.identity());
		var deadlineDescription1 = DeadlineDescriptions.of(13, new byte[] { 4, 5, 6 }, hashing);
		String encoded = new DeadlineDescriptions.Encoder().encode(deadlineDescription1);
		var deadlineDescription2 = new DeadlineDescriptions.Decoder().decode(encoded);
		assertEquals(deadlineDescription1, deadlineDescription2);
	}

	static {
		String current = System.getProperty("java.util.logging.config.file");
		if (current == null) {
			// if the property is not set, we provide a default (if it exists)
			URL resource = DeadlineDescriptionsTests.class.getClassLoader().getResource("logging.properties");
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