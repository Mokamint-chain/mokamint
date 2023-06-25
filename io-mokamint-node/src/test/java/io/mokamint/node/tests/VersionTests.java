package io.mokamint.node.tests;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.IOException;
import java.net.URL;
import java.util.logging.LogManager;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import io.mokamint.node.Versions;
import jakarta.websocket.DecodeException;
import jakarta.websocket.EncodeException;

public class VersionTests {

	@Test
	@DisplayName("versions are correctly encoded into Json and decoded from Json")
	public void encodeDecodeWorksForVersion() throws EncodeException, DecodeException {
		var version1 = Versions.of(1, 2, 3);
		String encoded = new Versions.Encoder().encode(version1);
		var version2 = new Versions.Decoder().decode(encoded);
		assertEquals(version1, version2);
	}

	static {
		String current = System.getProperty("java.util.logging.config.file");
		if (current == null) {
			// if the property is not set, we provide a default (if it exists)
			URL resource = VersionTests.class.getClassLoader().getResource("logging.properties");
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