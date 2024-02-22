/*
Copyright 2024 Fausto Spoto

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

public class ApplicationServiceTests extends AbstractLoggedTests {
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

	/*
	@Test
	@DisplayName("if a getPeerInfos() request reaches the service, it sends back the peers of the node")
	public void serviceGetPeerInfosWorks() throws DeploymentException, IOException, URISyntaxException, InterruptedException, TimeoutException, ClosedNodeException, NoSuchAlgorithmException {
		var semaphore = new Semaphore(0);
		var peerInfo1 = PeerInfos.of(Peers.of(new URI("ws://my.machine:8032")), 345, true);
		var peerInfo2 = PeerInfos.of(Peers.of(new URI("ws://her.machine:8033")), 11, false);
		var node = mkNode();
		when(node.getPeerInfos()).thenReturn(Stream.of(peerInfo1, peerInfo2));

		class MyTestClient extends RemoteApplicationImpl {

			public MyTestClient() throws DeploymentException, IOException {
				super(URI, 2000L, 240000L, 1000);
			}

			@Override
			protected void onGetPeerInfosResult(Stream<PeerInfo> received) {
				var peerInfos = received.collect(Collectors.toList());
				if (peerInfos.size() == 2 && peerInfos.contains(peerInfo1) && peerInfos.contains(peerInfo2))
					semaphore.release();
			}

			private void sendGetPeerInfos() throws ClosedNodeException {
				sendGetPeerInfos("id");
			}
		}

		try (var service = PublicNodeServices.open(node, PORT); var client = new MyTestClient()) {
			client.sendGetPeerInfos();
			assertTrue(semaphore.tryAcquire(1, 1, TimeUnit.SECONDS));
		}
	}
	*/
}