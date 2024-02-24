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

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.mock;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Arrays;
import java.util.concurrent.TimeoutException;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import io.hotmoka.annotations.ThreadSafe;
import io.hotmoka.testing.AbstractLoggedTests;
import io.mokamint.application.api.ApplicationException;
import io.mokamint.application.messages.CheckPrologExtraResultMessages;
import io.mokamint.application.messages.api.CheckPrologExtraMessage;
import io.mokamint.application.remote.RemoteApplications;
import io.mokamint.application.service.internal.ApplicationServiceImpl;
import jakarta.websocket.DeploymentException;
import jakarta.websocket.Session;

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

	/**
	 * Test server implementation.
	 */
	@ThreadSafe
	private static class PublicTestServer extends ApplicationServiceImpl {

		/**
		 * Creates a new test server.
		 * 
		 * @throws DeploymentException if the service cannot be deployed
		 * @throws IOException if an I/O error occurs
		 */
		private PublicTestServer() throws DeploymentException, IOException {
			super(mock(), PORT);
		}
	}

	@Test
	@DisplayName("checkPrologExtra() works")
	public void checkPrologExtraWorks() throws DeploymentException, IOException, ApplicationException, TimeoutException, InterruptedException {
		boolean result1 = true;
		var extra = new byte[] { 13, 1, 19, 73 };

		class MyServer extends PublicTestServer {

			private MyServer() throws DeploymentException, IOException {}

			@Override
			protected void onCheckPrologExtra(CheckPrologExtraMessage message, Session session) {
				if (Arrays.equals(extra, message.getExtra())) {
					try {
						sendObjectAsync(session, CheckPrologExtraResultMessages.of(result1, message.getId()));
					}
					catch (IOException e) {}
				}
			}
		};

		try (var service = new MyServer(); var remote = RemoteApplications.of(URI, TIME_OUT)) {
			var result2 = remote.checkPrologExtra(extra);
			assertSame(result1, result2);
		}
	}
}