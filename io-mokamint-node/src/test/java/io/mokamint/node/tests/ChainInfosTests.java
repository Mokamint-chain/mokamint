package io.mokamint.node.tests;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.IOException;
import java.net.URL;
import java.security.NoSuchAlgorithmException;
import java.util.Optional;
import java.util.logging.LogManager;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import io.mokamint.node.ChainInfos;
import jakarta.websocket.DecodeException;
import jakarta.websocket.EncodeException;

public class ChainInfosTests {

	@Test
	@DisplayName("chain infos with non-empty hashes are correctly encoded into Json and decoded from Json")
	public void encodeDecodeWorksForNonEmpty() throws EncodeException, DecodeException, NoSuchAlgorithmException {
		var info1 = ChainInfos.of(1317L, Optional.of(new byte[] { 3, 6, 8, 11 }), Optional.of(new byte[] { 0, 90, 91 }));
		String encoded = new ChainInfos.Encoder().encode(info1);
		var info2 = new ChainInfos.Decoder().decode(encoded);
		assertEquals(info1, info2);
	}

	@Test
	@DisplayName("chain infos with empty hashes are correctly encoded into Json and decoded from Json")
	public void encodeDecodeWorksForEmpty1() throws EncodeException, DecodeException, NoSuchAlgorithmException {
		var info1 = ChainInfos.of(1317L, Optional.empty(), Optional.of(new byte[] { 0, 90, 91 }));
		String encoded = new ChainInfos.Encoder().encode(info1);
		var info2 = new ChainInfos.Decoder().decode(encoded);
		assertEquals(info1, info2);
	}

	@Test
	@DisplayName("chain infos with empty hashes are correctly encoded into Json and decoded from Json")
	public void encodeDecodeWorksForEmpty2() throws EncodeException, DecodeException, NoSuchAlgorithmException {
		var info1 = ChainInfos.of(1317L, Optional.of(new byte[] { 0, 90, 91 }), Optional.empty());
		String encoded = new ChainInfos.Encoder().encode(info1);
		var info2 = new ChainInfos.Decoder().decode(encoded);
		assertEquals(info1, info2);
	}

	@Test
	@DisplayName("chain infos with empty hashes are correctly encoded into Json and decoded from Json")
	public void encodeDecodeWorksForEmpty3() throws EncodeException, DecodeException, NoSuchAlgorithmException {
		var info1 = ChainInfos.of(1317L, Optional.empty(), Optional.empty());
		String encoded = new ChainInfos.Encoder().encode(info1);
		var info2 = new ChainInfos.Decoder().decode(encoded);
		assertEquals(info1, info2);
	}

	static {
		String current = System.getProperty("java.util.logging.config.file");
		if (current == null) {
			// if the property is not set, we provide a default (if it exists)
			URL resource = ChainInfosTests.class.getClassLoader().getResource("logging.properties");
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