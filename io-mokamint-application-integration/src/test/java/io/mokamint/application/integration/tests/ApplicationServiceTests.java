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

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import io.hotmoka.testing.AbstractLoggedTests;
import io.mokamint.application.api.Application;
import io.mokamint.application.api.ApplicationException;
import io.mokamint.application.remote.internal.RemoteApplicationImpl;
import io.mokamint.application.service.ApplicationServices;
import jakarta.websocket.DeploymentException;

public class ApplicationServiceTests extends AbstractLoggedTests {
	private final static URI URI;
	private final static int PORT = 8030;
	private final static long TIME_OUT = 2000L;

	static {
		try {
			URI = new URI("ws://localhost:" + PORT);
		}
		catch (URISyntaxException e) {
			throw new RuntimeException(e);
		}
	}

	private Application mkApplication() {
		return mock();
	}

	@Test
	@DisplayName("if a checkPrologExtra() request reaches the service, it sends back the result of the check")
	public void serviceCheckPrologExtraWorks() throws ApplicationException, TimeoutException, InterruptedException, DeploymentException, IOException {
		var semaphore = new Semaphore(0);
		var app = mkApplication();
		var extra = new byte[] { 13, 1, 19, 73 };
		when(app.checkPrologExtra(eq(extra))).thenReturn(true);

		class MyTestClient extends RemoteApplicationImpl {

			public MyTestClient() throws DeploymentException, IOException {
				super(URI, TIME_OUT);
			}

			@Override
			protected void onCheckPrologExtraResult(boolean result) {
				if (result)
					semaphore.release();
			}

			private void sendCheckPrologExtra() throws ApplicationException {
				sendCheckPrologExtra(extra, "id");
			}
		}

		try (var service = ApplicationServices.open(app, PORT); var client = new MyTestClient()) {
			client.sendCheckPrologExtra();
			assertTrue(semaphore.tryAcquire(1, 1, TimeUnit.SECONDS));
		}
	}
}