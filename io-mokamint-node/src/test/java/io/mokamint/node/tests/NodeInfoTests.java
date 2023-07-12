package io.mokamint.node.tests;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.IOException;
import java.net.URL;
import java.util.UUID;
import java.util.logging.LogManager;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import io.mokamint.node.NodeInfos;
import io.mokamint.node.Versions;
import jakarta.websocket.DecodeException;
import jakarta.websocket.EncodeException;

public class NodeInfoTests {

	@Test
	@DisplayName("node informations are correctly encoded into Json and decoded from Json")
	public void encodeDecodeWorksForNodeInfo() throws EncodeException, DecodeException {
		var info1 = NodeInfos.of(Versions.of(1, 2, 3), UUID.randomUUID());
		String encoded = new NodeInfos.Encoder().encode(info1);
		var info2 = new NodeInfos.Decoder().decode(encoded);
		assertEquals(info1, info2);
	}

	static {
		String current = System.getProperty("java.util.logging.config.file");
		if (current == null) {
			// if the property is not set, we provide a default (if it exists)
			URL resource = NodeInfoTests.class.getClassLoader().getResource("logging.properties");
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