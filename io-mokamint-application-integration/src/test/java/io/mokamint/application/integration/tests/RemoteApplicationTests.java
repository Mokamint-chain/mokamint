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

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.fail;
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
import io.hotmoka.websockets.beans.ExceptionMessages;
import io.mokamint.application.api.ApplicationException;
import io.mokamint.application.messages.CheckPrologExtraResultMessages;
import io.mokamint.application.messages.CheckTransactionResultMessages;
import io.mokamint.application.messages.GetInitialStateIdResultMessages;
import io.mokamint.application.messages.GetPriorityResultMessages;
import io.mokamint.application.messages.GetRepresentationResultMessages;
import io.mokamint.application.messages.api.CheckPrologExtraMessage;
import io.mokamint.application.messages.api.CheckTransactionMessage;
import io.mokamint.application.messages.api.GetInitialStateIdMessage;
import io.mokamint.application.messages.api.GetPriorityMessage;
import io.mokamint.application.messages.api.GetRepresentationMessage;
import io.mokamint.application.remote.RemoteApplications;
import io.mokamint.application.service.internal.ApplicationServiceImpl;
import io.mokamint.node.Transactions;
import io.mokamint.node.api.RejectedTransactionException;
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

	private final static long TIME_OUT = 2000L;

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

	@Test
	@DisplayName("checkTransaction() works")
	public void checkTransactionWorks() throws DeploymentException, IOException, ApplicationException, TimeoutException, InterruptedException {
		var transaction = Transactions.of(new byte[] { 13, 1, 19, 73 });

		class MyServer extends PublicTestServer {

			private MyServer() throws DeploymentException, IOException {}

			@Override
			protected void onCheckTransaction(CheckTransactionMessage message, Session session) {
				if (transaction.equals(message.getTransaction())) {
					try {
						sendObjectAsync(session, CheckTransactionResultMessages.of(message.getId()));
					}
					catch (IOException e) {}
				}
			}
		};

		try (var service = new MyServer(); var remote = RemoteApplications.of(URI, TIME_OUT)) {
			remote.checkTransaction(transaction);
		}
		catch (RejectedTransactionException e) {
			fail();
		}
	}

	@Test
	@DisplayName("checkTransaction() works if it throws RejectedTransactionException")
	public void getTransactionWorksInCaseOfRejectedTransactionException() throws ApplicationException, InterruptedException, DeploymentException, IOException  {
		var transaction = Transactions.of(new byte[] { 13, 1, 19, 73 });
		var exceptionMessage = "rejected";

		class MyServer extends PublicTestServer {

			private MyServer() throws DeploymentException, IOException {}

			@Override
			protected void onCheckTransaction(CheckTransactionMessage message, Session session) {
				if (transaction.equals(message.getTransaction())) {
					try {
						sendObjectAsync(session, ExceptionMessages.of(new RejectedTransactionException(exceptionMessage), message.getId()));
					}
					catch (IOException e) {}
				}
			}
		};

		try (var service = new MyServer(); var remote = RemoteApplications.of(URI, TIME_OUT)) {
			var exception = assertThrows(RejectedTransactionException.class, () -> remote.checkTransaction(transaction));
			assertEquals(exceptionMessage, exception.getMessage());
		}
	}

	@Test
	@DisplayName("getPriority() works")
	public void getPriorityWorks() throws DeploymentException, IOException, ApplicationException, TimeoutException, InterruptedException, RejectedTransactionException {
		var transaction = Transactions.of(new byte[] { 13, 1, 19, 73 });
		var priority = 42L;

		class MyServer extends PublicTestServer {

			private MyServer() throws DeploymentException, IOException {}

			@Override
			protected void onGetPriority(GetPriorityMessage message, Session session) {
				if (transaction.equals(message.getTransaction())) {
					try {
						sendObjectAsync(session, GetPriorityResultMessages.of(priority, message.getId()));
					}
					catch (IOException e) {}
				}
			}
		};

		try (var service = new MyServer(); var remote = RemoteApplications.of(URI, TIME_OUT)) {
			assertEquals(priority, remote.getPriority(transaction));
		}
	}

	@Test
	@DisplayName("getPriority() works if it throws RejectedTransactionException")
	public void getPriorityWorksInCaseOfRejectedTransactionException() throws ApplicationException, InterruptedException, DeploymentException, IOException  {
		var transaction = Transactions.of(new byte[] { 13, 1, 19, 73 });
		var exceptionMessage = "rejected";

		class MyServer extends PublicTestServer {

			private MyServer() throws DeploymentException, IOException {}

			@Override
			protected void onGetPriority(GetPriorityMessage message, Session session) {
				if (transaction.equals(message.getTransaction())) {
					try {
						sendObjectAsync(session, ExceptionMessages.of(new RejectedTransactionException(exceptionMessage), message.getId()));
					}
					catch (IOException e) {}
				}
			}
		};

		try (var service = new MyServer(); var remote = RemoteApplications.of(URI, TIME_OUT)) {
			var exception = assertThrows(RejectedTransactionException.class, () -> remote.getPriority(transaction));
			assertEquals(exceptionMessage, exception.getMessage());
		}
	}

	@Test
	@DisplayName("getRepresentation() works")
	public void getRepresentationWorks() throws DeploymentException, IOException, ApplicationException, TimeoutException, InterruptedException, RejectedTransactionException {
		var transaction = Transactions.of(new byte[] { 13, 1, 19, 73 });
		var representation = "this is the wonderful representation";

		class MyServer extends PublicTestServer {

			private MyServer() throws DeploymentException, IOException {}

			@Override
			protected void onGetRepresentation(GetRepresentationMessage message, Session session) {
				if (transaction.equals(message.getTransaction())) {
					try {
						sendObjectAsync(session, GetRepresentationResultMessages.of(representation, message.getId()));
					}
					catch (IOException e) {}
				}
			}
		};

		try (var service = new MyServer(); var remote = RemoteApplications.of(URI, TIME_OUT)) {
			assertEquals(representation, remote.getRepresentation(transaction));
		}
	}

	@Test
	@DisplayName("getRepresentation() works if it throws RejectedTransactionException")
	public void getRepresentationWorksInCaseOfRejectedTransactionException() throws ApplicationException, InterruptedException, DeploymentException, IOException  {
		var transaction = Transactions.of(new byte[] { 13, 1, 19, 73 });
		var exceptionMessage = "rejected";

		class MyServer extends PublicTestServer {

			private MyServer() throws DeploymentException, IOException {}

			@Override
			protected void onGetRepresentation(GetRepresentationMessage message, Session session) {
				if (transaction.equals(message.getTransaction())) {
					try {
						sendObjectAsync(session, ExceptionMessages.of(new RejectedTransactionException(exceptionMessage), message.getId()));
					}
					catch (IOException e) {}
				}
			}
		};

		try (var service = new MyServer(); var remote = RemoteApplications.of(URI, TIME_OUT)) {
			var exception = assertThrows(RejectedTransactionException.class, () -> remote.getRepresentation(transaction));
			assertEquals(exceptionMessage, exception.getMessage());
		}
	}

	@Test
	@DisplayName("getInitialStateId() works")
	public void getInitialStateIdWorks() throws DeploymentException, IOException, ApplicationException, TimeoutException, InterruptedException, RejectedTransactionException {
		byte[] initialStateId = { 13, 1, 19, 73 };

		class MyServer extends PublicTestServer {

			private MyServer() throws DeploymentException, IOException {}

			@Override
			protected void onGetInitialStateId(GetInitialStateIdMessage message, Session session) {
				try {
					sendObjectAsync(session, GetInitialStateIdResultMessages.of(initialStateId, message.getId()));
				}
				catch (IOException e) {}
			}
		};

		try (var service = new MyServer(); var remote = RemoteApplications.of(URI, TIME_OUT)) {
			assertArrayEquals(initialStateId, remote.getInitialStateId());
		}
	}

	@Test
	@DisplayName("getInitialStateId() works if it throws ApplicationException")
	public void getInitialStateIdWorksInCaseOfApplicationException() throws ApplicationException, InterruptedException, DeploymentException, IOException  {
		var exceptionMessage = "failed";

		class MyServer extends PublicTestServer {

			private MyServer() throws DeploymentException, IOException {}

			@Override
			protected void onGetInitialStateId(GetInitialStateIdMessage message, Session session) {
				try {
					sendObjectAsync(session, ExceptionMessages.of(new ApplicationException(exceptionMessage), message.getId()));
				}
				catch (IOException e) {}
			}
		};

		try (var service = new MyServer(); var remote = RemoteApplications.of(URI, TIME_OUT)) {
			var exception = assertThrows(ApplicationException.class, () -> remote.getInitialStateId());
			assertEquals(exceptionMessage, exception.getMessage());
		}
	}
}