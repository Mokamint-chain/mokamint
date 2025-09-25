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

import static io.hotmoka.crypto.HashingAlgorithms.sha256;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.fail;
import static org.mockito.Mockito.mock;

import java.math.BigInteger;
import java.net.URI;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Optional;
import java.util.Random;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.stream.Stream;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import io.hotmoka.annotations.ThreadSafe;
import io.hotmoka.crypto.HashingAlgorithms;
import io.hotmoka.crypto.SignatureAlgorithms;
import io.hotmoka.testing.AbstractLoggedTests;
import io.hotmoka.websockets.api.FailedDeploymentException;
import io.hotmoka.websockets.beans.ExceptionMessages;
import io.mokamint.application.Infos;
import io.mokamint.application.api.UnknownGroupIdException;
import io.mokamint.application.api.UnknownStateException;
import io.mokamint.application.messages.AbortBlockResultMessages;
import io.mokamint.application.messages.BeginBlockResultMessages;
import io.mokamint.application.messages.CheckDeadlineResultMessages;
import io.mokamint.application.messages.CheckTransactionResultMessages;
import io.mokamint.application.messages.CommitBlockResultMessages;
import io.mokamint.application.messages.DeliverTransactionResultMessages;
import io.mokamint.application.messages.EndBlockResultMessages;
import io.mokamint.application.messages.GetBalanceResultMessages;
import io.mokamint.application.messages.GetInfoResultMessages;
import io.mokamint.application.messages.GetInitialStateIdResultMessages;
import io.mokamint.application.messages.GetPriorityResultMessages;
import io.mokamint.application.messages.GetRepresentationResultMessages;
import io.mokamint.application.messages.KeepFromResultMessages;
import io.mokamint.application.messages.PublishResultMessages;
import io.mokamint.application.messages.api.AbortBlockMessage;
import io.mokamint.application.messages.api.BeginBlockMessage;
import io.mokamint.application.messages.api.CheckDeadlineMessage;
import io.mokamint.application.messages.api.CheckTransactionMessage;
import io.mokamint.application.messages.api.CommitBlockMessage;
import io.mokamint.application.messages.api.DeliverTransactionMessage;
import io.mokamint.application.messages.api.EndBlockMessage;
import io.mokamint.application.messages.api.GetBalanceMessage;
import io.mokamint.application.messages.api.GetInfoMessage;
import io.mokamint.application.messages.api.GetInitialStateIdMessage;
import io.mokamint.application.messages.api.GetPriorityMessage;
import io.mokamint.application.messages.api.GetRepresentationMessage;
import io.mokamint.application.messages.api.KeepFromMessage;
import io.mokamint.application.messages.api.PublishMessage;
import io.mokamint.application.remote.RemoteApplications;
import io.mokamint.application.service.internal.ApplicationServiceImpl;
import io.mokamint.node.BlockDescriptions;
import io.mokamint.node.Blocks;
import io.mokamint.node.Transactions;
import io.mokamint.node.api.TransactionRejectedException;
import io.mokamint.nonce.Challenges;
import io.mokamint.nonce.Deadlines;
import io.mokamint.nonce.Prologs;
import jakarta.websocket.Session;

public class RemoteApplicationTests extends AbstractLoggedTests {
	private final static int PORT = 8030;
	private final static URI URI = java.net.URI.create("ws://localhost:" + PORT);
	private final static int TIME_OUT = 2000;

	/**
	 * Test server implementation.
	 */
	@ThreadSafe
	private static class PublicTestServer extends ApplicationServiceImpl {

		/**
		 * Creates a new test server.
		 * 
		 * @throws FailedDeploymentException if the service cannot be deployed
		 */
		private PublicTestServer() throws FailedDeploymentException {
			super(mock(), PORT);
		}
	}

	@Test
	@DisplayName("checkDeadline() works")
	public void checkDeadlineWorks() throws Exception {
		boolean result1 = true;
		var hashingForDeadlines = HashingAlgorithms.shabal256();
		var hashingForGenerations = HashingAlgorithms.sha256();
		var generationSignature = new byte[hashingForGenerations.length()];
		for (int pos = 0; pos < generationSignature.length; pos++)
			generationSignature[pos] = (byte) (42 + pos);
		var value = new byte[hashingForDeadlines.length()];
		for (int pos = 0; pos < value.length; pos++)
			value[pos] = (byte) pos;
		var ed25519 = SignatureAlgorithms.ed25519();
		var plotKeyPair = ed25519.getKeyPair();
		var prolog = Prologs.of("octopus", ed25519, ed25519.getKeyPair().getPublic(), ed25519, plotKeyPair.getPublic(), new byte[0]);
		var deadline = Deadlines.of(prolog, 13, value, Challenges.of(11, generationSignature, hashingForDeadlines, hashingForGenerations));

		class MyServer extends PublicTestServer {

			private MyServer() throws FailedDeploymentException {}

			@Override
			protected void onCheckDeadline(CheckDeadlineMessage message, Session session) {
				if (deadline.equals(message.getDeadline()))
					sendObjectAsync(session, CheckDeadlineResultMessages.of(result1, message.getId()), RuntimeException::new);
			}
		};

		try (var service = new MyServer(); var remote = RemoteApplications.of(URI, TIME_OUT)) {
			var result2 = remote.checkDeadline(deadline);
			assertSame(result1, result2);
		}
	}

	@Test
	@DisplayName("checkTransaction() works")
	public void checkTransactionWorks() throws Exception {
		var transaction = Transactions.of(new byte[] { 13, 1, 19, 73 });

		class MyServer extends PublicTestServer {

			private MyServer() throws FailedDeploymentException {}

			@Override
			protected void onCheckTransaction(CheckTransactionMessage message, Session session) {
				if (transaction.equals(message.getTransaction()))
					sendObjectAsync(session, CheckTransactionResultMessages.of(message.getId()), RuntimeException::new);
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
	public void checkTransactionWorksInCaseOfRejectedTransactionException() throws Exception  {
		var transaction = Transactions.of(new byte[] { 13, 1, 19, 73 });
		var exceptionMessage = "rejected";

		class MyServer extends PublicTestServer {

			private MyServer() throws FailedDeploymentException {}

			@Override
			protected void onCheckTransaction(CheckTransactionMessage message, Session session) {
				if (transaction.equals(message.getTransaction()))
					sendObjectAsync(session, ExceptionMessages.of(new TransactionRejectedException(exceptionMessage), message.getId()), RuntimeException::new);
			}
		};

		try (var service = new MyServer(); var remote = RemoteApplications.of(URI, TIME_OUT)) {
			var exception = assertThrows(TransactionRejectedException.class, () -> remote.checkTransaction(transaction));
			assertEquals(exceptionMessage, exception.getMessage());
		}
	}

	@Test
	@DisplayName("getInfo() works")
	public void getInfoWorks() throws Exception {
		var info = Infos.of("name", "description");

		class MyServer extends PublicTestServer {

			private MyServer() throws FailedDeploymentException {}

			@Override
			protected void onGetInfo(GetInfoMessage message, Session session) {
				sendObjectAsync(session, GetInfoResultMessages.of(info, message.getId()), RuntimeException::new);
			}
		};

		try (var service = new MyServer(); var remote = RemoteApplications.of(URI, TIME_OUT)) {
			assertEquals(info, remote.getInfo());
		}
	}

	@Test
	@DisplayName("getBalance() works if balance is present")
	public void getBalancePresentWorks() throws Exception {
		var ed25519 = SignatureAlgorithms.ed25519();
		var publicKey = ed25519.getKeyPair().getPublic();
		var balance = Optional.of(BigInteger.valueOf(42L));

		class MyServer extends PublicTestServer {

			private MyServer() throws FailedDeploymentException {}

			@Override
			protected void onGetBalance(GetBalanceMessage message, Session session) {
				if (ed25519.equals(message.getSignature()) && publicKey.equals(message.getPublicKey()))
					sendObjectAsync(session, GetBalanceResultMessages.of(balance, message.getId()), RuntimeException::new);
			}
		};

		try (var service = new MyServer(); var remote = RemoteApplications.of(URI, TIME_OUT)) {
			assertEquals(balance, remote.getBalance(ed25519, publicKey));
		}
	}

	@Test
	@DisplayName("getBalance() works if balance is missing")
	public void getBalanceMissingWorks() throws Exception {
		var ed25519 = SignatureAlgorithms.ed25519();
		var publicKey = ed25519.getKeyPair().getPublic();
		var balance = Optional.<BigInteger> empty();

		class MyServer extends PublicTestServer {

			private MyServer() throws FailedDeploymentException {}

			@Override
			protected void onGetBalance(GetBalanceMessage message, Session session) {
				if (ed25519.equals(message.getSignature()) && publicKey.equals(message.getPublicKey()))
					sendObjectAsync(session, GetBalanceResultMessages.of(balance, message.getId()), RuntimeException::new);
			}
		};

		try (var service = new MyServer(); var remote = RemoteApplications.of(URI, TIME_OUT)) {
			assertEquals(balance, remote.getBalance(ed25519, publicKey));
		}
	}

	@Test
	@DisplayName("getPriority() works")
	public void getPriorityWorks() throws Exception {
		var transaction = Transactions.of(new byte[] { 13, 1, 19, 73 });
		var priority = 42L;

		class MyServer extends PublicTestServer {

			private MyServer() throws FailedDeploymentException {}

			@Override
			protected void onGetPriority(GetPriorityMessage message, Session session) {
				if (transaction.equals(message.getTransaction()))
					sendObjectAsync(session, GetPriorityResultMessages.of(priority, message.getId()), RuntimeException::new);
			}
		};

		try (var service = new MyServer(); var remote = RemoteApplications.of(URI, TIME_OUT)) {
			assertEquals(priority, remote.getPriority(transaction));
		}
	}

	@Test
	@DisplayName("getPriority() works if it throws RejectedTransactionException")
	public void getPriorityWorksInCaseOfRejectedTransactionException() throws Exception  {
		var transaction = Transactions.of(new byte[] { 13, 1, 19, 73 });
		var exceptionMessage = "rejected";

		class MyServer extends PublicTestServer {

			private MyServer() throws FailedDeploymentException {}

			@Override
			protected void onGetPriority(GetPriorityMessage message, Session session) {
				if (transaction.equals(message.getTransaction()))
					sendObjectAsync(session, ExceptionMessages.of(new TransactionRejectedException(exceptionMessage), message.getId()), RuntimeException::new);
			}
		};

		try (var service = new MyServer(); var remote = RemoteApplications.of(URI, TIME_OUT)) {
			var exception = assertThrows(TransactionRejectedException.class, () -> remote.getPriority(transaction));
			assertEquals(exceptionMessage, exception.getMessage());
		}
	}

	@Test
	@DisplayName("getRepresentation() works")
	public void getRepresentationWorks() throws Exception {
		var transaction = Transactions.of(new byte[] { 13, 1, 19, 73 });
		var representation = "this is the wonderful representation";

		class MyServer extends PublicTestServer {

			private MyServer() throws FailedDeploymentException {}

			@Override
			protected void onGetRepresentation(GetRepresentationMessage message, Session session) {
				if (transaction.equals(message.getTransaction()))
					sendObjectAsync(session, GetRepresentationResultMessages.of(representation, message.getId()), RuntimeException::new);
			}
		};

		try (var service = new MyServer(); var remote = RemoteApplications.of(URI, TIME_OUT)) {
			assertEquals(representation, remote.getRepresentation(transaction));
		}
	}

	@Test
	@DisplayName("getRepresentation() works if it throws RejectedTransactionException")
	public void getRepresentationWorksInCaseOfRejectedTransactionException() throws Exception  {
		var transaction = Transactions.of(new byte[] { 13, 1, 19, 73 });
		var exceptionMessage = "rejected";

		class MyServer extends PublicTestServer {

			private MyServer() throws FailedDeploymentException {}

			@Override
			protected void onGetRepresentation(GetRepresentationMessage message, Session session) {
				if (transaction.equals(message.getTransaction()))
					sendObjectAsync(session, ExceptionMessages.of(new TransactionRejectedException(exceptionMessage), message.getId()), RuntimeException::new);
			}
		};

		try (var service = new MyServer(); var remote = RemoteApplications.of(URI, TIME_OUT)) {
			var exception = assertThrows(TransactionRejectedException.class, () -> remote.getRepresentation(transaction));
			assertEquals(exceptionMessage, exception.getMessage());
		}
	}

	@Test
	@DisplayName("getInitialStateId() works")
	public void getInitialStateIdWorks() throws Exception {
		byte[] initialStateId = { 13, 1, 19, 73 };

		class MyServer extends PublicTestServer {

			private MyServer() throws FailedDeploymentException {}

			@Override
			protected void onGetInitialStateId(GetInitialStateIdMessage message, Session session) {
				sendObjectAsync(session, GetInitialStateIdResultMessages.of(initialStateId, message.getId()), RuntimeException::new);
			}
		};

		try (var service = new MyServer(); var remote = RemoteApplications.of(URI, TIME_OUT)) {
			assertArrayEquals(initialStateId, remote.getInitialStateId());
		}
	}

	@Test
	@DisplayName("beginBlock() works")
	public void beginBlockWorks() throws Exception {
		var groupId = 42;
		var height = 13L;
		var when = LocalDateTime.now();
		var stateId = new byte[] { 13, 1, 19, 73 };

		class MyServer extends PublicTestServer {

			private MyServer() throws FailedDeploymentException {}

			@Override
			protected void onBeginBlock(BeginBlockMessage message, Session session) {
				if (message.getHeight() == height && when.equals(message.getWhen()) && Arrays.equals(stateId, message.getStateId()))
					sendObjectAsync(session, BeginBlockResultMessages.of(groupId, message.getId()), RuntimeException::new);
			}
		};

		try (var service = new MyServer(); var remote = RemoteApplications.of(URI, TIME_OUT)) {
			assertSame(groupId, remote.beginBlock(height, when, stateId));
		}
	}

	@Test
	@DisplayName("beginBlock() works if it throws UnknownStateException")
	public void beginBlockWorksInCaseOfUnknownStateException() throws Exception  {
		var height = 13L;
		var when = LocalDateTime.now();
		var stateId = new byte[] { 13, 1, 19, 73 };
		var exceptionMessage = "unknown state";

		class MyServer extends PublicTestServer {

			private MyServer() throws FailedDeploymentException {}

			@Override
			protected void onBeginBlock(BeginBlockMessage message, Session session) {
				if (message.getHeight() == height && when.equals(message.getWhen()) && Arrays.equals(stateId, message.getStateId()))
					sendObjectAsync(session, ExceptionMessages.of(new UnknownStateException(exceptionMessage), message.getId()), RuntimeException::new);
			}
		};

		try (var service = new MyServer(); var remote = RemoteApplications.of(URI, TIME_OUT)) {
			var exception = assertThrows(UnknownStateException.class, () -> remote.beginBlock(height, when, stateId));
			assertEquals(exceptionMessage, exception.getMessage());
		}
	}

	@Test
	@DisplayName("deliverTransaction() works")
	public void deliverTransactionWorks() throws Exception {
		var groupId = 42;
		var transaction = Transactions.of(new byte[] { 13, 1, 19, 73 });

		class MyServer extends PublicTestServer {

			private MyServer() throws FailedDeploymentException {}

			@Override
			protected void onDeliverTransaction(DeliverTransactionMessage message, Session session) {
				if (groupId == message.getGroupId() && transaction.equals(message.getTransaction()))
					sendObjectAsync(session, DeliverTransactionResultMessages.of(message.getId()), RuntimeException::new);
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
	public void deliverTransactionWorksInCaseOfUnknownGroupIdException() throws Exception  {
		var groupId = 42;
		var transaction = Transactions.of(new byte[] { 13, 1, 19, 73 });
		var exceptionMessage = "unknown group id";

		class MyServer extends PublicTestServer {

			private MyServer() throws FailedDeploymentException {}

			@Override
			protected void onDeliverTransaction(DeliverTransactionMessage message, Session session) {
				if (groupId == message.getGroupId() && transaction.equals(message.getTransaction()))
					sendObjectAsync(session, ExceptionMessages.of(new UnknownGroupIdException(exceptionMessage), message.getId()), RuntimeException::new);
			}
		};

		try (var service = new MyServer(); var remote = RemoteApplications.of(URI, TIME_OUT)) {
			var exception = assertThrows(UnknownGroupIdException.class, () -> remote.deliverTransaction(groupId, transaction));
			assertEquals(exceptionMessage, exception.getMessage());
		}
	}

	@Test
	@DisplayName("deliverTransaction() works if it throws RejectedTransactionException")
	public void deliverTransactionWorksInCaseOfRejectedTransactionException() throws Exception  {
		var groupId = 42;
		var transaction = Transactions.of(new byte[] { 13, 1, 19, 73 });
		var exceptionMessage = "rejected";

		class MyServer extends PublicTestServer {

			private MyServer() throws FailedDeploymentException {}

			@Override
			protected void onDeliverTransaction(DeliverTransactionMessage message, Session session) {
				if (groupId == message.getGroupId() && transaction.equals(message.getTransaction()))
					sendObjectAsync(session, ExceptionMessages.of(new TransactionRejectedException(exceptionMessage), message.getId()), RuntimeException::new);
			}
		};

		try (var service = new MyServer(); var remote = RemoteApplications.of(URI, TIME_OUT)) {
			var exception = assertThrows(TransactionRejectedException.class, () -> remote.deliverTransaction(groupId, transaction));
			assertEquals(exceptionMessage, exception.getMessage());
		}
	}

	@Test
	@DisplayName("endBlock works")
	public void endBlockWorks() throws Exception {
		var hashingForGenerations = sha256();
		var generationSignature = new byte[hashingForGenerations.length()];
		for (int pos = 0; pos < generationSignature.length; pos++)
			generationSignature[pos] = (byte) (42 + pos);
		var hashingForDeadlines = HashingAlgorithms.shabal256();
		var value = new byte[hashingForDeadlines.length()];
		for (int pos = 0; pos < value.length; pos++)
			value[pos] = (byte) pos;
		var ed25519 = SignatureAlgorithms.ed25519();
		var plotKeyPair = ed25519.getKeyPair();
		var prolog = Prologs.of("octopus", ed25519, ed25519.getKeyPair().getPublic(), ed25519, plotKeyPair.getPublic(), new byte[0]);
		var deadline = Deadlines.of(prolog, 13, value, Challenges.of(11, generationSignature, hashingForDeadlines, hashingForGenerations));
		byte[] finalStateId = { 24, 12, 20, 24 };
		var groupId = 42;

		class MyServer extends PublicTestServer {

			private MyServer() throws FailedDeploymentException {}

			@Override
			protected void onEndBlock(EndBlockMessage message, Session session) {
				if (message.getGroupId() == groupId && message.getDeadline().equals(deadline))
					sendObjectAsync(session, EndBlockResultMessages.of(finalStateId, message.getId()), RuntimeException::new);
			}
		};

		try (var service = new MyServer(); var remote = RemoteApplications.of(URI, TIME_OUT)) {
			assertArrayEquals(finalStateId, remote.endBlock(groupId, deadline));
		}
	}

	@Test
	@DisplayName("endBlock() works if it throws UnknownGroupIdException")
	public void endBlockWorksInCaseOfUnknownGroupIdException() throws Exception  {
		var hashingForGenerations = sha256();
		var generationSignature = new byte[hashingForGenerations.length()];
		for (int pos = 0; pos < generationSignature.length; pos++)
			generationSignature[pos] = (byte) (42 + pos);
		var hashingForDeadlines = HashingAlgorithms.shabal256();
		var value = new byte[hashingForDeadlines.length()];
		for (int pos = 0; pos < value.length; pos++)
			value[pos] = (byte) pos;
		var ed25519 = SignatureAlgorithms.ed25519();
		var plotKeyPair = ed25519.getKeyPair();
		var prolog = Prologs.of("octopus", ed25519, ed25519.getKeyPair().getPublic(), ed25519, plotKeyPair.getPublic(), new byte[0]);
		var deadline = Deadlines.of(prolog, 13, value, Challenges.of(11, generationSignature, hashingForDeadlines, hashingForGenerations));
		var groupId = 42;
		var exceptionMessage = "unknown group id";

		class MyServer extends PublicTestServer {

			private MyServer() throws FailedDeploymentException {}

			@Override
			protected void onEndBlock(EndBlockMessage message, Session session) {
				if (message.getGroupId() == groupId && message.getDeadline().equals(deadline))
					sendObjectAsync(session, ExceptionMessages.of(new UnknownGroupIdException(exceptionMessage), message.getId()), RuntimeException::new);
			}
		};

		try (var service = new MyServer(); var remote = RemoteApplications.of(URI, TIME_OUT)) {
			var exception = assertThrows(UnknownGroupIdException.class, () -> remote.endBlock(groupId, deadline));
			assertEquals(exceptionMessage, exception.getMessage());
		}
	}

	@Test
	@DisplayName("commitBlock() works")
	public void commitBlockWorks() throws Exception {
		var groupId = 42;

		class MyServer extends PublicTestServer {

			private MyServer() throws FailedDeploymentException {}

			@Override
			protected void onCommitBlock(CommitBlockMessage message, Session session) {
				sendObjectAsync(session, CommitBlockResultMessages.of(message.getId()), RuntimeException::new);
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
	public void commitBlockWorksInCaseOfUnknownGroupIdException() throws Exception  {
		var groupId = 42;
		var exceptionMessage = "unknown group id";

		class MyServer extends PublicTestServer {

			private MyServer() throws FailedDeploymentException {}

			@Override
			protected void onCommitBlock(CommitBlockMessage message, Session session) {
				sendObjectAsync(session, ExceptionMessages.of(new UnknownGroupIdException(exceptionMessage), message.getId()), RuntimeException::new);
			}
		};

		try (var service = new MyServer(); var remote = RemoteApplications.of(URI, TIME_OUT)) {
			var exception = assertThrows(UnknownGroupIdException.class, () -> remote.commitBlock(groupId));
			assertEquals(exceptionMessage, exception.getMessage());
		}
	}

	@Test
	@DisplayName("abortBlock() works")
	public void abortBlockWorks() throws Exception {
		var groupId = 42;

		class MyServer extends PublicTestServer {

			private MyServer() throws FailedDeploymentException {}

			@Override
			protected void onAbortBlock(AbortBlockMessage message, Session session) {
				sendObjectAsync(session, AbortBlockResultMessages.of(message.getId()), RuntimeException::new);
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
	public void abortBlockWorksInCaseOfUnknownGroupIdException() throws Exception  {
		var groupId = 42;
		var exceptionMessage = "unknown group id";

		class MyServer extends PublicTestServer {

			private MyServer() throws FailedDeploymentException {}

			@Override
			protected void onAbortBlock(AbortBlockMessage message, Session session) {
				sendObjectAsync(session, ExceptionMessages.of(new UnknownGroupIdException(exceptionMessage), message.getId()), RuntimeException::new);
			}
		};

		try (var service = new MyServer(); var remote = RemoteApplications.of(URI, TIME_OUT)) {
			var exception = assertThrows(UnknownGroupIdException.class, () -> remote.abortBlock(groupId));
			assertEquals(exceptionMessage, exception.getMessage());
		}
	}

	@Test
	@DisplayName("keepFrom() works")
	public void keepFromWorks() throws Exception {
		var start = LocalDateTime.now();

		class MyServer extends PublicTestServer {

			private MyServer() throws FailedDeploymentException {}

			@Override
			protected void onKeepFrom(KeepFromMessage message, Session session) {
				sendObjectAsync(session, KeepFromResultMessages.of(message.getId()), RuntimeException::new);
			}
		};

		try (var service = new MyServer(); var remote = RemoteApplications.of(URI, TIME_OUT)) {
			remote.keepFrom(start);
		}
	}

	@Test
	@DisplayName("if keepFrom() is slow, it leads to a time-out")
	public void keepFromWorksInCaseOfTimeout() throws Exception {
		var start = LocalDateTime.now();
		var finished = new Semaphore(0);

		class MyServer extends PublicTestServer {

			private MyServer() throws FailedDeploymentException {}

			@Override
			protected void onKeepFrom(KeepFromMessage message, Session session) {
				try {
					Thread.sleep(TIME_OUT * 4); // <----
				}
				catch (InterruptedException e) {
					Thread.currentThread().interrupt();
				}

				sendObjectAsync(session, KeepFromResultMessages.of(message.getId()), RuntimeException::new);
				finished.release();
			}
		};

		try (var service = new MyServer(); var remote = RemoteApplications.of(URI, TIME_OUT)) {
			assertThrows(TimeoutException.class, () -> remote.keepFrom(start));
			// we wait, in order to avoid shutting down the server before the handler completes
			finished.tryAcquire(1, TIME_OUT * 10, TimeUnit.MILLISECONDS);
		}
	}

	@Test
	@DisplayName("keepFrom() ignores unexpected exceptions")
	public void keepFromWorksInCaseOfUnexpectedException() throws Exception {
		class MyServer extends PublicTestServer {

			private MyServer() throws FailedDeploymentException {}

			@Override
			protected void onKeepFrom(KeepFromMessage message, Session session) {
				sendObjectAsync(session, ExceptionMessages.of(new IllegalArgumentException(), message.getId()), RuntimeException::new);
			}
		};

		var start = LocalDateTime.now();
		try (var service = new MyServer(); var remote = RemoteApplications.of(URI, TIME_OUT)) {
			assertThrows(TimeoutException.class, () -> remote.keepFrom(start));
		}
	}

	@Test
	@DisplayName("keepFrom() ignores unexpected messages")
	public void keepFromWorksInCaseOfUnexpectedMessage() throws Exception {
		class MyServer extends PublicTestServer {

			private MyServer() throws FailedDeploymentException {}

			@Override
			protected void onKeepFrom(KeepFromMessage message, Session session) {
				sendObjectAsync(session, GetRepresentationResultMessages.of("hello", message.getId()), RuntimeException::new);
			}
		};

		var start = LocalDateTime.now();
		try (var service = new MyServer(); var remote = RemoteApplications.of(URI, TIME_OUT)) {
			assertThrows(TimeoutException.class, () -> remote.keepFrom(start));
		}
	}

	@Test
	@DisplayName("publish() works")
	public void publish() throws Exception {
		var random = new Random();
		var ed25519 = SignatureAlgorithms.ed25519();
		var sha256 = HashingAlgorithms.sha256();

		var keysDeadline = ed25519.getKeyPair();
		var keysBlock = ed25519.getKeyPair();
		var prolog = Prologs.of("octopus", ed25519, keysBlock.getPublic(), ed25519, keysDeadline.getPublic(), new byte[5]);
		var previousHash = new byte[sha256.length()];
		random.nextBytes(previousHash);
		var value = new byte[sha256.length()];
		random.nextBytes(value);
		var generationSignature = new byte[sha256.length()];
		random.nextBytes(generationSignature);
		var challenge = Challenges.of(47, generationSignature, sha256, sha256);
		var deadline = Deadlines.of(prolog, 42L, value, challenge);
		var description = BlockDescriptions.of(42L, BigInteger.TEN, 1000L, 180L, BigInteger.TWO, deadline, previousHash, 4000, 256, sha256, sha256);
		var bytes1 = new byte[100];
		random.nextBytes(bytes1);
		var bytes2 = new byte[60];
		random.nextBytes(bytes2);
		var bytes3 = new byte[113];
		random.nextBytes(bytes3);
		var transaction1 = Transactions.of(bytes1);
		var transaction2 = Transactions.of(bytes2);
		var transaction3 = Transactions.of(bytes3);
		var stateId = new byte[87];
		random.nextBytes(stateId);
		var block = Blocks.of(description, Stream.of(transaction1, transaction2, transaction3), stateId, keysBlock.getPrivate());

		class MyServer extends PublicTestServer {

			private MyServer() throws FailedDeploymentException {}

			@Override
			protected void onPublish(PublishMessage message, Session session) {
				sendObjectAsync(session, PublishResultMessages.of(message.getId()), RuntimeException::new);
			}
		};

		try (var service = new MyServer(); var remote = RemoteApplications.of(URI, TIME_OUT)) {
			remote.publish(block);
		}
	}

	@Test
	@DisplayName("if publish() is slow, it leads to a time-out")
	public void publishWorksInCaseOfTimeout() throws Exception {
		var random = new Random();
		var ed25519 = SignatureAlgorithms.ed25519();
		var sha256 = HashingAlgorithms.sha256();

		var keysDeadline = ed25519.getKeyPair();
		var keysBlock = ed25519.getKeyPair();
		var prolog = Prologs.of("octopus", ed25519, keysBlock.getPublic(), ed25519, keysDeadline.getPublic(), new byte[5]);
		var previousHash = new byte[sha256.length()];
		random.nextBytes(previousHash);
		var value = new byte[sha256.length()];
		random.nextBytes(value);
		var generationSignature = new byte[sha256.length()];
		random.nextBytes(generationSignature);
		var challenge = Challenges.of(47, generationSignature, sha256, sha256);
		var deadline = Deadlines.of(prolog, 42L, value, challenge);
		var description = BlockDescriptions.of(42L, BigInteger.TEN, 1000L, 180L, BigInteger.TWO, deadline, previousHash, 4000, 256, sha256, sha256);
		var bytes1 = new byte[100];
		random.nextBytes(bytes1);
		var bytes2 = new byte[60];
		random.nextBytes(bytes2);
		var bytes3 = new byte[113];
		random.nextBytes(bytes3);
		var transaction1 = Transactions.of(bytes1);
		var transaction2 = Transactions.of(bytes2);
		var transaction3 = Transactions.of(bytes3);
		var stateId = new byte[87];
		random.nextBytes(stateId);
		var block = Blocks.of(description, Stream.of(transaction1, transaction2, transaction3), stateId, keysBlock.getPrivate());

		var finished = new Semaphore(0);

		class MyServer extends PublicTestServer {

			private MyServer() throws FailedDeploymentException {}

			@Override
			protected void onPublish(PublishMessage message, Session session) {
				try {
					Thread.sleep(TIME_OUT * 4); // <----
				}
				catch (InterruptedException e) {
					Thread.currentThread().interrupt();
				}

				sendObjectAsync(session, PublishResultMessages.of(message.getId()), RuntimeException::new);
				finished.release();
			}
		};

		try (var service = new MyServer(); var remote = RemoteApplications.of(URI, TIME_OUT)) {
			assertThrows(TimeoutException.class, () -> remote.publish(block));
			// we wait, in order to avoid shutting down the server before the handler completes
			finished.tryAcquire(1, TIME_OUT * 10, TimeUnit.MILLISECONDS);
		}
	}

	@Test
	@DisplayName("publish() ignores unexpected exceptions")
	public void publishWorksInCaseOfUnexpectedException() throws Exception {
		var random = new Random();
		var ed25519 = SignatureAlgorithms.ed25519();
		var sha256 = HashingAlgorithms.sha256();

		var keysDeadline = ed25519.getKeyPair();
		var keysBlock = ed25519.getKeyPair();
		var prolog = Prologs.of("octopus", ed25519, keysBlock.getPublic(), ed25519, keysDeadline.getPublic(), new byte[5]);
		var previousHash = new byte[sha256.length()];
		random.nextBytes(previousHash);
		var value = new byte[sha256.length()];
		random.nextBytes(value);
		var generationSignature = new byte[sha256.length()];
		random.nextBytes(generationSignature);
		var challenge = Challenges.of(47, generationSignature, sha256, sha256);
		var deadline = Deadlines.of(prolog, 42L, value, challenge);
		var description = BlockDescriptions.of(42L, BigInteger.TEN, 1000L, 180L, BigInteger.TWO, deadline, previousHash, 4000, 256, sha256, sha256);
		var bytes1 = new byte[100];
		random.nextBytes(bytes1);
		var bytes2 = new byte[60];
		random.nextBytes(bytes2);
		var bytes3 = new byte[113];
		random.nextBytes(bytes3);
		var transaction1 = Transactions.of(bytes1);
		var transaction2 = Transactions.of(bytes2);
		var transaction3 = Transactions.of(bytes3);
		var stateId = new byte[87];
		random.nextBytes(stateId);
		var block = Blocks.of(description, Stream.of(transaction1, transaction2, transaction3), stateId, keysBlock.getPrivate());

		class MyServer extends PublicTestServer {

			private MyServer() throws FailedDeploymentException {}

			@Override
			protected void onPublish(PublishMessage message, Session session) {
				sendObjectAsync(session, ExceptionMessages.of(new IllegalArgumentException(), message.getId()), RuntimeException::new);
			}
		};

		try (var service = new MyServer(); var remote = RemoteApplications.of(URI, TIME_OUT)) {
			assertThrows(TimeoutException.class, () -> remote.publish(block));
		}
	}

	@Test
	@DisplayName("publish() ignores unexpected messages")
	public void publishWorksInCaseOfUnexpectedMessage() throws Exception {
		var random = new Random();
		var ed25519 = SignatureAlgorithms.ed25519();
		var sha256 = HashingAlgorithms.sha256();

		var keysDeadline = ed25519.getKeyPair();
		var keysBlock = ed25519.getKeyPair();
		var prolog = Prologs.of("octopus", ed25519, keysBlock.getPublic(), ed25519, keysDeadline.getPublic(), new byte[5]);
		var previousHash = new byte[sha256.length()];
		random.nextBytes(previousHash);
		var value = new byte[sha256.length()];
		random.nextBytes(value);
		var generationSignature = new byte[sha256.length()];
		random.nextBytes(generationSignature);
		var challenge = Challenges.of(47, generationSignature, sha256, sha256);
		var deadline = Deadlines.of(prolog, 42L, value, challenge);
		var description = BlockDescriptions.of(42L, BigInteger.TEN, 1000L, 180L, BigInteger.TWO, deadline, previousHash, 4000, 256, sha256, sha256);
		var bytes1 = new byte[100];
		random.nextBytes(bytes1);
		var bytes2 = new byte[60];
		random.nextBytes(bytes2);
		var bytes3 = new byte[113];
		random.nextBytes(bytes3);
		var transaction1 = Transactions.of(bytes1);
		var transaction2 = Transactions.of(bytes2);
		var transaction3 = Transactions.of(bytes3);
		var stateId = new byte[87];
		random.nextBytes(stateId);
		var block = Blocks.of(description, Stream.of(transaction1, transaction2, transaction3), stateId, keysBlock.getPrivate());

		class MyServer extends PublicTestServer {

			private MyServer() throws FailedDeploymentException {}

			@Override
			protected void onPublish(PublishMessage message, Session session) {
				sendObjectAsync(session, GetRepresentationResultMessages.of("hello", message.getId()), RuntimeException::new);
			}
		};

		try (var service = new MyServer(); var remote = RemoteApplications.of(URI, TIME_OUT)) {
			assertThrows(TimeoutException.class, () -> remote.publish(block));
		}
	}
}