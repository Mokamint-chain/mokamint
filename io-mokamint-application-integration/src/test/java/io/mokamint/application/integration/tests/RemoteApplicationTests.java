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

package io.mokamint.application.integration.tests;

import java.net.URI;
import java.net.URISyntaxException;

import io.hotmoka.testing.AbstractLoggedTests;

public class RemoteApplicationTests extends AbstractLoggedTests {
	private final static URI URI;
	private final static int PORT = 8030;

	static {
		try {
			URI = new URI("ws://localhost:" + PORT);
		}
		catch (URISyntaxException e) {
			throw new RuntimeException(e);
		}
	}

	private final static long TIME_OUT = 500L;

	/*@Test
	@DisplayName("getPeerInfos() works")
	public void getPeerInfosWorks() throws DeploymentException, IOException, URISyntaxException, TimeoutException, InterruptedException, ClosedNodeException {
		var peerInfos1 = Set.of(PeerInfos.of(Peers.of(new URI("ws://my.machine:1024")), 345, true),
				PeerInfos.of(Peers.of(new URI("ws://your.machine:1025")), 11, false));

		class MyServer extends PublicTestServer {

			private MyServer() throws DeploymentException, IOException {}

			@Override
			protected void onGetPeerInfos(GetPeerInfosMessage message, Session session) {
				try {
					sendObjectAsync(session, GetPeerInfosResultMessages.of(peerInfos1.stream(), message.getId()));
				}
				catch (IOException e) {}
			}
		};

		try (var service = new MyServer(); var remote = RemotePublicNodes.of(URI, TIME_OUT)) {
			var peerInfos2 = remote.getPeerInfos();
			assertEquals(peerInfos1, peerInfos2.collect(Collectors.toSet()));
		}
	}*/
}