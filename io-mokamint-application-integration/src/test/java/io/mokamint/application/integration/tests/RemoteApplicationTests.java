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
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.SignatureException;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.concurrent.TimeoutException;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import io.hotmoka.annotations.ThreadSafe;
import io.hotmoka.crypto.HashingAlgorithms;
import io.hotmoka.crypto.SignatureAlgorithms;
import io.hotmoka.testing.AbstractLoggedTests;
import io.hotmoka.websockets.beans.ExceptionMessages;
import io.mokamint.application.api.ApplicationException;
import io.mokamint.application.api.UnknownGroupIdException;
import io.mokamint.application.api.UnknownStateException;
import io.mokamint.application.messages.AbortBlockResultMessages;
import io.mokamint.application.messages.BeginBlockResultMessages;
import io.mokamint.application.messages.CheckPrologExtraResultMessages;
import io.mokamint.application.messages.CheckTransactionResultMessages;
import io.mokamint.application.messages.CommitBlockResultMessages;
import io.mokamint.application.messages.DeliverTransactionResultMessages;
import io.mokamint.application.messages.EndBlockResultMessages;
import io.mokamint.application.messages.GetInitialStateIdResultMessages;
import io.mokamint.application.messages.GetPriorityResultMessages;
import io.mokamint.application.messages.GetRepresentationResultMessages;
import io.mokamint.application.messages.KeepFromResultMessages;
import io.mokamint.application.messages.api.AbortBlockMessage;
import io.mokamint.application.messages.api.BeginBlockMessage;
import io.mokamint.application.messages.api.CheckPrologExtraMessage;
import io.mokamint.application.messages.api.CheckTransactionMessage;
import io.mokamint.application.messages.api.CommitBlockMessage;
import io.mokamint.application.messages.api.DeliverTransactionMessage;
import io.mokamint.application.messages.api.EndBlockMessage;
import io.mokamint.application.messages.api.GetInitialStateIdMessage;
import io.mokamint.application.messages.api.GetPriorityMessage;
import io.mokamint.application.messages.api.GetRepresentationMessage;
import io.mokamint.application.messages.api.KeepFromMessage;
import io.mokamint.application.remote.RemoteApplications;
import io.mokamint.application.service.internal.ApplicationServiceImpl;
import io.mokamint.node.Transactions;
import io.mokamint.node.api.TransactionRejectedException;
import io.mokamint.nonce.Challenges;
import io.mokamint.nonce.Deadlines;
import io.mokamint.nonce.Prologs;
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

	private final static int TIME_OUT = 2000;

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
		catch (TransactionRejectedException e) {
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
						sendObjectAsync(session, ExceptionMessages.of(new TransactionRejectedException(exceptionMessage), message.getId()));
					}
					catch (IOException e) {}
				}
			}
		};

		try (var service = new MyServer(); var remote = RemoteApplications.of(URI, TIME_OUT)) {
			var exception = assertThrows(TransactionRejectedException.class, () -> remote.checkTransaction(transaction));
			assertEquals(exceptionMessage, exception.getMessage());
		}
	}

	@Test
	@DisplayName("getPriority() works")
	public void getPriorityWorks() throws DeploymentException, IOException, ApplicationException, TimeoutException, InterruptedException, TransactionRejectedException {
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
						sendObjectAsync(session, ExceptionMessages.of(new TransactionRejectedException(exceptionMessage), message.getId()));
					}
					catch (IOException e) {}
				}
			}
		};

		try (var service = new MyServer(); var remote = RemoteApplications.of(URI, TIME_OUT)) {
			var exception = assertThrows(TransactionRejectedException.class, () -> remote.getPriority(transaction));
			assertEquals(exceptionMessage, exception.getMessage());
		}
	}

	@Test
	@DisplayName("getRepresentation() works")
	public void getRepresentationWorks() throws DeploymentException, IOException, ApplicationException, TimeoutException, InterruptedException, TransactionRejectedException {
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
						sendObjectAsync(session, ExceptionMessages.of(new TransactionRejectedException(exceptionMessage), message.getId()));
					}
					catch (IOException e) {}
				}
			}
		};

		try (var service = new MyServer(); var remote = RemoteApplications.of(URI, TIME_OUT)) {
			var exception = assertThrows(TransactionRejectedException.class, () -> remote.getRepresentation(transaction));
			assertEquals(exceptionMessage, exception.getMessage());
		}
	}

	@Test
	@DisplayName("getInitialStateId() works")
	public void getInitialStateIdWorks() throws DeploymentException, IOException, ApplicationException, TimeoutException, InterruptedException, TransactionRejectedException {
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

	@Test
	@DisplayName("beginBlock() works")
	public void beginBlockWorks() throws DeploymentException, IOException, ApplicationException, TimeoutException, InterruptedException, UnknownStateException {
		var groupId = 42;
		var height = 13L;
		var when = LocalDateTime.now();
		var stateId = new byte[] { 13, 1, 19, 73 };

		class MyServer extends PublicTestServer {

			private MyServer() throws DeploymentException, IOException {}

			@Override
			protected void onBeginBlock(BeginBlockMessage message, Session session) {
				if (message.getHeight() == height && when.equals(message.getWhen()) && Arrays.equals(stateId, message.getStateId())) {
					try {
						sendObjectAsync(session, BeginBlockResultMessages.of(groupId, message.getId()));
					}
					catch (IOException e) {}
				}
			}
		};

		try (var service = new MyServer(); var remote = RemoteApplications.of(URI, TIME_OUT)) {
			assertSame(groupId, remote.beginBlock(height, when, stateId));
		}
	}

	@Test
	@DisplayName("beginBlock() works if it throws UnknownStateException")
	public void beginBlockWorksInCaseOfUnknownStateException() throws ApplicationException, InterruptedException, DeploymentException, IOException  {
		var height = 13L;
		var when = LocalDateTime.now();
		var stateId = new byte[] { 13, 1, 19, 73 };
		var exceptionMessage = "unknown state";

		class MyServer extends PublicTestServer {

			private MyServer() throws DeploymentException, IOException {}

			@Override
			protected void onBeginBlock(BeginBlockMessage message, Session session) {
				if (message.getHeight() == height && when.equals(message.getWhen()) && Arrays.equals(stateId, message.getStateId())) {
					try {
						sendObjectAsync(session, ExceptionMessages.of(new UnknownStateException(exceptionMessage), message.getId()));
					}
					catch (IOException e) {}
				}
			}
		};

		try (var service = new MyServer(); var remote = RemoteApplications.of(URI, TIME_OUT)) {
			var exception = assertThrows(UnknownStateException.class, () -> remote.beginBlock(height, when, stateId));
			assertEquals(exceptionMessage, exception.getMessage());
		}
	}

	@Test
	@DisplayName("deliverTransaction() works")
	public void deliverTransactionWorks() throws DeploymentException, IOException, ApplicationException, TimeoutException, InterruptedException, UnknownGroupIdException, TransactionRejectedException {
		var groupId = 42;
		var transaction = Transactions.of(new byte[] { 13, 1, 19, 73 });

		class MyServer extends PublicTestServer {

			private MyServer() throws DeploymentException, IOException {}

			@Override
			protected void onDeliverTransaction(DeliverTransactionMessage message, Session session) {
				if (groupId == message.getGroupId() && transaction.equals(message.getTransaction())) {
					try {
						sendObjectAsync(session, DeliverTransactionResultMessages.of(message.getId()));
					}
					catch (IOException e) {}
				}
			}
		};

		try (var service = new MyServer(); var remote = RemoteApplications.of(URI, TIME_OUT)) {
			remote.deliverTransaction(groupId, transaction);
		}
		catch (UnknownGroupIdException | TransactionRejectedException e) {
			fail();
		}
	}

	@Test
	@DisplayName("deliverTransaction() works if it throws UnknownGroupIdException")
	public void deliverTransactionWorksInCaseOfUnknownGroupIdException() throws ApplicationException, InterruptedException, DeploymentException, IOException  {
		var groupId = 42;
		var transaction = Transactions.of(new byte[] { 13, 1, 19, 73 });
		var exceptionMessage = "unknown group id";

		class MyServer extends PublicTestServer {

			private MyServer() throws DeploymentException, IOException {}

			@Override
			protected void onDeliverTransaction(DeliverTransactionMessage message, Session session) {
				if (groupId == message.getGroupId() && transaction.equals(message.getTransaction())) {
					try {
						sendObjectAsync(session, ExceptionMessages.of(new UnknownGroupIdException(exceptionMessage), message.getId()));
					}
					catch (IOException e) {}
				}
			}
		};

		try (var service = new MyServer(); var remote = RemoteApplications.of(URI, TIME_OUT)) {
			var exception = assertThrows(UnknownGroupIdException.class, () -> remote.deliverTransaction(groupId, transaction));
			assertEquals(exceptionMessage, exception.getMessage());
		}
	}

	@Test
	@DisplayName("deliverTransaction() works if it throws RejectedTransactionException")
	public void deliverTransactionWorksInCaseOfRejectedTransactionException() throws ApplicationException, InterruptedException, DeploymentException, IOException  {
		var groupId = 42;
		var transaction = Transactions.of(new byte[] { 13, 1, 19, 73 });
		var exceptionMessage = "rejected";

		class MyServer extends PublicTestServer {

			private MyServer() throws DeploymentException, IOException {}

			@Override
			protected void onDeliverTransaction(DeliverTransactionMessage message, Session session) {
				if (groupId == message.getGroupId() && transaction.equals(message.getTransaction())) {
					try {
						sendObjectAsync(session, ExceptionMessages.of(new TransactionRejectedException(exceptionMessage), message.getId()));
					}
					catch (IOException e) {}
				}
			}
		};

		try (var service = new MyServer(); var remote = RemoteApplications.of(URI, TIME_OUT)) {
			var exception = assertThrows(TransactionRejectedException.class, () -> remote.deliverTransaction(groupId, transaction));
			assertEquals(exceptionMessage, exception.getMessage());
		}
	}

	@Test
	@DisplayName("endBlock works")
	public void endBlockWorks() throws DeploymentException, IOException, ApplicationException, TimeoutException, InterruptedException, UnknownGroupIdException, NoSuchAlgorithmException, InvalidKeyException, SignatureException {
		var hashing = HashingAlgorithms.shabal256();
		var value = new byte[hashing.length()];
		for (int pos = 0; pos < value.length; pos++)
			value[pos] = (byte) pos;
		var ed25519 = SignatureAlgorithms.ed25519();
		var plotKeyPair = ed25519.getKeyPair();
		var prolog = Prologs.of("octopus", ed25519, ed25519.getKeyPair().getPublic(), ed25519, plotKeyPair.getPublic(), new byte[0]);
		var deadline = Deadlines.of(prolog, 13, value, Challenges.of(11, new byte[] { 90, 91, 92 }, hashing), plotKeyPair.getPrivate());
		byte[] finalStateId = { 24, 12, 20, 24 };
		var groupId = 42;

		class MyServer extends PublicTestServer {

			private MyServer() throws DeploymentException, IOException {}

			@Override
			protected void onEndBlock(EndBlockMessage message, Session session) {
				if (message.getGroupId() == groupId && message.getDeadline().equals(deadline)) {
					try {
						sendObjectAsync(session, EndBlockResultMessages.of(finalStateId, message.getId()));
					}
					catch (IOException e) {}
				}
			}
		};

		try (var service = new MyServer(); var remote = RemoteApplications.of(URI, TIME_OUT)) {
			assertArrayEquals(finalStateId, remote.endBlock(groupId, deadline));
		}
	}

	@Test
	@DisplayName("endBlock() works if it throws UnknownGroupIdException")
	public void endBlockWorksInCaseOfUnknownGroupIdException() throws ApplicationException, InterruptedException, DeploymentException, IOException, NoSuchAlgorithmException, InvalidKeyException, SignatureException  {
		var hashing = HashingAlgorithms.shabal256();
		var value = new byte[hashing.length()];
		for (int pos = 0; pos < value.length; pos++)
			value[pos] = (byte) pos;
		var ed25519 = SignatureAlgorithms.ed25519();
		var plotKeyPair = ed25519.getKeyPair();
		var prolog = Prologs.of("octopus", ed25519, ed25519.getKeyPair().getPublic(), ed25519, plotKeyPair.getPublic(), new byte[0]);
		var deadline = Deadlines.of(prolog, 13, value, Challenges.of(11, new byte[] { 90, 91, 92 }, hashing), plotKeyPair.getPrivate());
		var groupId = 42;
		var exceptionMessage = "unknown group id";

		class MyServer extends PublicTestServer {

			private MyServer() throws DeploymentException, IOException {}

			@Override
			protected void onEndBlock(EndBlockMessage message, Session session) {
				if (message.getGroupId() == groupId && message.getDeadline().equals(deadline)) {
					try {
						sendObjectAsync(session, ExceptionMessages.of(new UnknownGroupIdException(exceptionMessage), message.getId()));
					}
					catch (IOException e) {}
				}
			}
		};

		try (var service = new MyServer(); var remote = RemoteApplications.of(URI, TIME_OUT)) {
			var exception = assertThrows(UnknownGroupIdException.class, () -> remote.endBlock(groupId, deadline));
			assertEquals(exceptionMessage, exception.getMessage());
		}
	}

	@Test
	@DisplayName("commitBlock() works")
	public void commitBlockWorks() throws DeploymentException, IOException, ApplicationException, TimeoutException, InterruptedException {
		var groupId = 42;

		class MyServer extends PublicTestServer {

			private MyServer() throws DeploymentException, IOException {}

			@Override
			protected void onCommitBlock(CommitBlockMessage message, Session session) {
				try {
					sendObjectAsync(session, CommitBlockResultMessages.of(message.getId()));
				}
				catch (IOException e) {}
			}
		};

		try (var service = new MyServer(); var remote = RemoteApplications.of(URI, TIME_OUT)) {
			remote.commitBlock(groupId);
		}
		catch (UnknownGroupIdException e) {
			fail();
		}
	}

	@Test
	@DisplayName("commitBlock() works if it throws UnknownGroupIdException")
	public void commitBlockWorksInCaseOfUnknownGroupIdException() throws ApplicationException, InterruptedException, DeploymentException, IOException  {
		var groupId = 42;
		var exceptionMessage = "unknown group id";

		class MyServer extends PublicTestServer {

			private MyServer() throws DeploymentException, IOException {}

			@Override
			protected void onCommitBlock(CommitBlockMessage message, Session session) {
				try {
					sendObjectAsync(session, ExceptionMessages.of(new UnknownGroupIdException(exceptionMessage), message.getId()));
				}
				catch (IOException e) {}
			}
		};

		try (var service = new MyServer(); var remote = RemoteApplications.of(URI, TIME_OUT)) {
			var exception = assertThrows(UnknownGroupIdException.class, () -> remote.commitBlock(groupId));
			assertEquals(exceptionMessage, exception.getMessage());
		}
	}

	@Test
	@DisplayName("abortBlock() works")
	public void abortBlockWorks() throws DeploymentException, IOException, ApplicationException, TimeoutException, InterruptedException {
		var groupId = 42;

		class MyServer extends PublicTestServer {

			private MyServer() throws DeploymentException, IOException {}

			@Override
			protected void onAbortBlock(AbortBlockMessage message, Session session) {
				try {
					sendObjectAsync(session, AbortBlockResultMessages.of(message.getId()));
				}
				catch (IOException e) {}
			}
		};

		try (var service = new MyServer(); var remote = RemoteApplications.of(URI, TIME_OUT)) {
			remote.abortBlock(groupId);
		}
		catch (UnknownGroupIdException e) {
			fail();
		}
	}

	@Test
	@DisplayName("abortBlock() works if it throws UnknownGroupIdException")
	public void abortBlockWorksInCaseOfUnknownGroupIdException() throws ApplicationException, InterruptedException, DeploymentException, IOException  {
		var groupId = 42;
		var exceptionMessage = "unknown group id";

		class MyServer extends PublicTestServer {

			private MyServer() throws DeploymentException, IOException {}

			@Override
			protected void onAbortBlock(AbortBlockMessage message, Session session) {
				try {
					sendObjectAsync(session, ExceptionMessages.of(new UnknownGroupIdException(exceptionMessage), message.getId()));
				}
				catch (IOException e) {}
			}
		};

		try (var service = new MyServer(); var remote = RemoteApplications.of(URI, TIME_OUT)) {
			var exception = assertThrows(UnknownGroupIdException.class, () -> remote.abortBlock(groupId));
			assertEquals(exceptionMessage, exception.getMessage());
		}
	}

	@Test
	@DisplayName("keepFrom() works")
	public void keepFromWorks() throws DeploymentException, IOException, ApplicationException, TimeoutException, InterruptedException {
		var start = LocalDateTime.now();

		class MyServer extends PublicTestServer {

			private MyServer() throws DeploymentException, IOException {}

			@Override
			protected void onKeepFrom(KeepFromMessage message, Session session) {
				try {
					sendObjectAsync(session, KeepFromResultMessages.of(message.getId()));
				}
				catch (IOException e) {}
			}
		};

		try (var service = new MyServer(); var remote = RemoteApplications.of(URI, TIME_OUT)) {
			remote.keepFrom(start);
		}
	}

	@Test
	@DisplayName("keepFrom() works if it throws ApplicationException")
	public void keepFromWorksInCaseOfApplicationException() throws ApplicationException, InterruptedException, DeploymentException, IOException  {
		var start = LocalDateTime.now();

		var exceptionMessage = "misbehaving application";

		class MyServer extends PublicTestServer {

			private MyServer() throws DeploymentException, IOException {}

			@Override
			protected void onKeepFrom(KeepFromMessage message, Session session) {
				try {
					sendObjectAsync(session, ExceptionMessages.of(new ApplicationException(exceptionMessage), message.getId()));
				}
				catch (IOException e) {}
			}
		};

		try (var service = new MyServer(); var remote = RemoteApplications.of(URI, TIME_OUT)) {
			var exception = assertThrows(ApplicationException.class, () -> remote.keepFrom(start));
			assertEquals(exceptionMessage, exception.getMessage());
		}
	}
}