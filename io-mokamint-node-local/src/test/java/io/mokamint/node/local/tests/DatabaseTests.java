/*
Copyright 2023 Fausto Spoto

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

    http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
*/

package io.mokamint.node.local.tests;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Path;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.Set;
import java.util.logging.LogManager;
import java.util.stream.Collectors;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import io.mokamint.node.Blocks;
import io.mokamint.node.Peers;
import io.mokamint.node.api.DatabaseException;
import io.mokamint.node.local.Config;
import io.mokamint.node.local.internal.Database;

public class DatabaseTests {

	private static Config mkConfig(Path dir) throws NoSuchAlgorithmException {
		return Config.Builder.defaults()
			.setDir(dir)
			.build();
	}

	@Test
	@DisplayName("peers added to the database are still there at next opening")
	public void peersAreKeptForNextOpening(@TempDir Path dir) throws NoSuchAlgorithmException, DatabaseException, URISyntaxException {
		var peer1 = Peers.of(new URI("ws://localhost:8030"));
		var peer2 = Peers.of(new URI("ws://www.mokamint.io:8032"));
		var config = mkConfig(dir);

		try (var db = new Database(config)) {
			assertTrue(db.getPeers().count() == 0);
			assertTrue(db.add(peer1, true));
			assertTrue(db.add(peer2, true));
		}

		try (var db = new Database(config)) {
			assertEquals(Set.of(peer1, peer2), db.getPeers().collect(Collectors.toSet()));
		}
	}

	@Test
	@DisplayName("peers removed from the database are not there at next opening")
	public void removedPeersAreNotInNextOpening(@TempDir Path dir) throws NoSuchAlgorithmException, DatabaseException, URISyntaxException {
		var peer1 = Peers.of(new URI("ws://localhost:8030"));
		var peer2 = Peers.of(new URI("ws://www.mokamint.io:8032"));
		var peer3 = Peers.of(new URI("ws://www.amazon.com:8032"));
		var config = mkConfig(dir);

		try (var db = new Database(config)) {
			assertTrue(db.getPeers().count() == 0);
			assertTrue(db.add(peer1, true));
			assertTrue(db.add(peer2, true));
			assertTrue(db.add(peer3, true));
			assertTrue(db.remove(peer2));
		}

		try (var db = new Database(config)) {
			assertEquals(Set.of(peer1, peer3), db.getPeers().collect(Collectors.toSet()));
		}
	}

	@Test
	@DisplayName("duplicate peers are kept only once")
	public void peersHaveNoDuplicates(@TempDir Path dir) throws NoSuchAlgorithmException, DatabaseException, URISyntaxException {
		var peer1 = Peers.of(new URI("ws://localhost:8030"));
		var peer2 = Peers.of(new URI("ws://www.mokamint.io:8032"));
		var config = mkConfig(dir);

		try (var db = new Database(config)) {
			assertTrue(db.getPeers().count() == 0);
			assertTrue(db.add(peer1, true));
			assertTrue(db.add(peer2, true));
			assertFalse(db.add(peer1, true));
			assertFalse(db.add(peer2, true));
			assertEquals(Set.of(peer1, peer2), db.getPeers().collect(Collectors.toSet()));
		}
	}

	@Test
	@DisplayName("the first genesis block added to the database becomes head and genesis of the chain")
	public void firstGenesisBlockBecomesHeadAndGenesis(@TempDir Path dir) throws NoSuchAlgorithmException, DatabaseException, URISyntaxException {
		var genesis = Blocks.genesis(LocalDateTime.now(ZoneId.of("UTC")));
		var config = mkConfig(dir);

		try (var db = new Database(config)) {
			db.add(genesis);
			assertEquals(genesis, db.getGenesis().get());
			assertEquals(genesis, db.getHead().get());
		}
	}

	@Test
	@DisplayName("if the genesis of the chain is set, a subsequent genesis block is not added")
	public void ifGenesisIsSetNextGenesisBlockIsRejected(@TempDir Path dir) throws NoSuchAlgorithmException, DatabaseException, URISyntaxException {
		var genesis1 = Blocks.genesis(LocalDateTime.now(ZoneId.of("UTC")));
		var genesis2 = Blocks.genesis(LocalDateTime.now(ZoneId.of("UTC")).plus(1, ChronoUnit.MINUTES));
		var config = mkConfig(dir);

		try (var db = new Database(config)) {
			assertTrue(db.add(genesis1));
			assertFalse(db.add(genesis2));
			assertEquals(genesis1, db.getGenesis().get());
			assertEquals(genesis1, db.getHead().get());
		}
	}

	static {
		String current = System.getProperty("java.util.logging.config.file");
		if (current == null) {
			// if the property is not set, we provide a default (if it exists)
			URL resource = DatabaseTests.class.getClassLoader().getResource("logging.properties");
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