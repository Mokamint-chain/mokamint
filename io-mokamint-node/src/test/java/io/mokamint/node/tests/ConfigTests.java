package io.mokamint.node.tests;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.IOException;
import java.net.URL;
import java.security.NoSuchAlgorithmException;
import java.util.logging.LogManager;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import io.mokamint.node.BaseConsensusConfig;
import io.mokamint.node.ConsensusConfigs;
import jakarta.websocket.DecodeException;
import jakarta.websocket.EncodeException;

public class ConfigTests {

	@Test
	@DisplayName("configs are correctly encoded into Json and decoded from Json")
	public void encodeDecodeWorks() throws EncodeException, DecodeException, NoSuchAlgorithmException {
		var config1 = BaseConsensusConfig.Builder.defaults().build();
		String encoded = new ConsensusConfigs.Encoder().encode(config1);
		var config2 = new ConsensusConfigs.Decoder().decode(encoded);
		assertEquals(config1, config2);
	}

	static {
		String current = System.getProperty("java.util.logging.config.file");
		if (current == null) {
			// if the property is not set, we provide a default (if it exists)
			URL resource = ConfigTests.class.getClassLoader().getResource("logging.properties");
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