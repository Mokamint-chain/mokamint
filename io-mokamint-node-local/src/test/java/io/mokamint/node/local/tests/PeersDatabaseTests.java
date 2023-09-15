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
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Path;
import java.security.NoSuchAlgorithmException;
import java.util.Set;
import java.util.stream.Collectors;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import io.hotmoka.testing.AbstractLoggedTests;
import io.mokamint.node.Peers;
import io.mokamint.node.api.DatabaseException;
import io.mokamint.node.local.Config;
import io.mokamint.node.local.internal.ClosedDatabaseException;
import io.mokamint.node.local.internal.LocalNodeImpl;
import io.mokamint.node.local.internal.PeersDatabase;

public class PeersDatabaseTests extends AbstractLoggedTests {

	private static PeersDatabase mkDatabase(Path dir) throws NoSuchAlgorithmException, DatabaseException {
		var config = Config.Builder.defaults()
			.setDir(dir)
			.build();

		var node = mock(LocalNodeImpl.class);
		when(node.getConfig()).thenReturn(config);

		return new PeersDatabase(node);
	}

	@Test
	@DisplayName("peers added to the database are still there at next opening")
	public void peersAreKeptForNextOpening(@TempDir Path dir) throws NoSuchAlgorithmException, DatabaseException, URISyntaxException, InterruptedException, ClosedDatabaseException {
		var peer1 = Peers.of(new URI("ws://localhost:8030"));
		var peer2 = Peers.of(new URI("ws://www.mokamint.io:8032"));

		try (var db = mkDatabase(dir)) {
			assertTrue(db.getPeers().count() == 0);
			assertTrue(db.add(peer1, true));
			assertTrue(db.add(peer2, true));
		}

		try (var db = mkDatabase(dir)) {
			assertEquals(Set.of(peer1, peer2), db.getPeers().collect(Collectors.toSet()));
		}
	}

	@Test
	@DisplayName("peers removed from the database are not there at next opening")
	public void removedPeersAreNotInNextOpening(@TempDir Path dir) throws NoSuchAlgorithmException, DatabaseException, URISyntaxException, InterruptedException, ClosedDatabaseException {
		var peer1 = Peers.of(new URI("ws://localhost:8030"));
		var peer2 = Peers.of(new URI("ws://www.mokamint.io:8032"));
		var peer3 = Peers.of(new URI("ws://www.amazon.com:8032"));

		try (var db = mkDatabase(dir)) {
			assertTrue(db.getPeers().count() == 0);
			assertTrue(db.add(peer1, true));
			assertTrue(db.add(peer2, true));
			assertTrue(db.add(peer3, true));
			assertTrue(db.remove(peer2));
		}

		try (var db = mkDatabase(dir)) {
			assertEquals(Set.of(peer1, peer3), db.getPeers().collect(Collectors.toSet()));
		}
	}

	@Test
	@DisplayName("duplicate peers are kept only once")
	public void peersHaveNoDuplicates(@TempDir Path dir) throws NoSuchAlgorithmException, DatabaseException, URISyntaxException, InterruptedException, ClosedDatabaseException {
		var peer1 = Peers.of(new URI("ws://localhost:8030"));
		var peer2 = Peers.of(new URI("ws://www.mokamint.io:8032"));

		try (var db = mkDatabase(dir)) {
			assertTrue(db.getPeers().count() == 0);
			assertTrue(db.add(peer1, true));
			assertTrue(db.add(peer2, true));
			assertFalse(db.add(peer1, true));
			assertFalse(db.add(peer2, true));
			assertEquals(Set.of(peer1, peer2), db.getPeers().collect(Collectors.toSet()));
		}
	}
}