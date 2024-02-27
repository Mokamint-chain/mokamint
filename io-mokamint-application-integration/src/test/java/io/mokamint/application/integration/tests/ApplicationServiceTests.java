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
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.SignatureException;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import io.hotmoka.crypto.HashingAlgorithms;
import io.hotmoka.crypto.SignatureAlgorithms;
import io.hotmoka.testing.AbstractLoggedTests;
import io.hotmoka.websockets.beans.api.ExceptionMessage;
import io.mokamint.application.api.Application;
import io.mokamint.application.api.ApplicationException;
import io.mokamint.application.api.UnknownGroupIdException;
import io.mokamint.application.api.UnknownStateException;
import io.mokamint.application.remote.internal.RemoteApplicationImpl;
import io.mokamint.application.service.ApplicationServices;
import io.mokamint.node.Transactions;
import io.mokamint.node.api.RejectedTransactionException;
import io.mokamint.node.api.Transaction;
import io.mokamint.nonce.Deadlines;
import io.mokamint.nonce.Prologs;
import jakarta.websocket.DeploymentException;

public class ApplicationServiceTests extends AbstractLoggedTests {
	private final static URI URI;
	private final static int PORT = 8030;
	private final static long TIME_OUT = 2000L;
	private final static String ID = "id";

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

	@Test
	@DisplayName("if a checkTransaction() request reaches the service, it checks the transaction")
	public void serviceCheckTransactionWorks() throws RejectedTransactionException, ApplicationException, TimeoutException, InterruptedException, DeploymentException, IOException {
		var semaphore = new Semaphore(0);
		var app = mkApplication();
		var transaction = Transactions.of(new byte[] { 13, 1, 19, 73 });
		doNothing().when(app).checkTransaction(eq(transaction));

		class MyTestClient extends RemoteApplicationImpl {

			public MyTestClient() throws DeploymentException, IOException {
				super(URI, TIME_OUT);
			}

			@Override
			protected void onCheckTransactionResult() {
				semaphore.release();
			}

			private void sendCheckTransaction() throws ApplicationException {
				sendCheckTransaction(transaction, "id");
			}
		}

		try (var service = ApplicationServices.open(app, PORT); var client = new MyTestClient()) {
			client.sendCheckTransaction();
			assertTrue(semaphore.tryAcquire(1, 1, TimeUnit.SECONDS));
		}
	}

	@Test
	@DisplayName("if a checkTransaction() request reaches the service and the transaction is rejected, it sends back an exception")
	public void serviceCheckTransactionRejectedTransactionWorks() throws RejectedTransactionException, ApplicationException, TimeoutException, InterruptedException, DeploymentException, IOException {
		var semaphore = new Semaphore(0);
		var app = mkApplication();
		var transaction = Transactions.of(new byte[] { 13, 1, 19, 73 });
		var exceptionMessage = "rejected";
		doThrow(new RejectedTransactionException(exceptionMessage)).when(app).checkTransaction(eq(transaction));
	
		class MyTestClient extends RemoteApplicationImpl {
	
			public MyTestClient() throws DeploymentException, IOException {
				super(URI, TIME_OUT);
			}
	
			@Override
			protected void onException(ExceptionMessage message) {
				if (RejectedTransactionException.class.isAssignableFrom(message.getExceptionClass()) && exceptionMessage.equals(message.getMessage()))
					semaphore.release();
			}
	
			private void sendCheckTransaction(Transaction transaction) throws ApplicationException {
				sendCheckTransaction(transaction, "id");
			}
		}
	
		try (var service = ApplicationServices.open(app, PORT); var client = new MyTestClient()) {
			client.sendCheckTransaction(transaction);
			assertTrue(semaphore.tryAcquire(1, 1, TimeUnit.SECONDS));
		}
	}

	@Test
	@DisplayName("if a getPriority() request reaches the service, it yields the priority of the transaction")
	public void serviceGetPriorityWorks() throws RejectedTransactionException, ApplicationException, TimeoutException, InterruptedException, DeploymentException, IOException {
		var semaphore = new Semaphore(0);
		var app = mkApplication();
		var transaction = Transactions.of(new byte[] { 13, 1, 19, 73 });
		when(app.getPriority(eq(transaction))).thenReturn(42L);

		class MyTestClient extends RemoteApplicationImpl {

			public MyTestClient() throws DeploymentException, IOException {
				super(URI, TIME_OUT);
			}

			@Override
			protected void onGetPriorityResult(long result) {
				if (result == 42L)
					semaphore.release();
			}

			private void sendGetPriority() throws ApplicationException {
				sendGetPriority(transaction, "id");
			}
		}

		try (var service = ApplicationServices.open(app, PORT); var client = new MyTestClient()) {
			client.sendGetPriority();
			assertTrue(semaphore.tryAcquire(1, 1, TimeUnit.SECONDS));
		}
	}

	@Test
	@DisplayName("if a getPriority() request reaches the service and the transaction is rejected, it sends back an exception")
	public void serviceGetPriorityRejectedTransactionWorks() throws RejectedTransactionException, ApplicationException, TimeoutException, InterruptedException, DeploymentException, IOException {
		var semaphore = new Semaphore(0);
		var app = mkApplication();
		var transaction = Transactions.of(new byte[] { 13, 1, 19, 73 });
		String exceptionMessage = "rejected";
		when(app.getPriority(eq(transaction))).thenThrow(new RejectedTransactionException(exceptionMessage));
	
		class MyTestClient extends RemoteApplicationImpl {
	
			public MyTestClient() throws DeploymentException, IOException {
				super(URI, TIME_OUT);
			}
	
			@Override
			protected void onException(ExceptionMessage message) {
				if (RejectedTransactionException.class.isAssignableFrom(message.getExceptionClass()) && exceptionMessage.equals(message.getMessage()))
					semaphore.release();
			}
	
			private void sendGetPriority(Transaction transaction) throws ApplicationException {
				sendGetPriority(transaction, "id");
			}
		}
	
		try (var service = ApplicationServices.open(app, PORT); var client = new MyTestClient()) {
			client.sendGetPriority(transaction);
			assertTrue(semaphore.tryAcquire(1, 1, TimeUnit.SECONDS));
		}
	}

	@Test
	@DisplayName("if a getRepresentation() request reaches the service, it yields the representation of the transaction")
	public void serviceGetRepresentationWorks() throws RejectedTransactionException, ApplicationException, TimeoutException, InterruptedException, DeploymentException, IOException {
		var semaphore = new Semaphore(0);
		var app = mkApplication();
		var transaction = Transactions.of(new byte[] { 13, 1, 19, 73 });
		var representation = "this is the wonderful representation";
		when(app.getRepresentation(eq(transaction))).thenReturn(representation);

		class MyTestClient extends RemoteApplicationImpl {

			public MyTestClient() throws DeploymentException, IOException {
				super(URI, TIME_OUT);
			}

			@Override
			protected void onGetRepresentationResult(String result) {
				if (representation.equals(result))
					semaphore.release();
			}

			private void sendGetRepresentation() throws ApplicationException {
				sendGetRepresentation(transaction, "id");
			}
		}

		try (var service = ApplicationServices.open(app, PORT); var client = new MyTestClient()) {
			client.sendGetRepresentation();
			assertTrue(semaphore.tryAcquire(1, 1, TimeUnit.SECONDS));
		}
	}

	@Test
	@DisplayName("if a getRepresentation() request reaches the service and the transaction is rejected, it sends back an exception")
	public void serviceGetRepresentationRejectedTransactionWorks() throws RejectedTransactionException, ApplicationException, TimeoutException, InterruptedException, DeploymentException, IOException {
		var semaphore = new Semaphore(0);
		var app = mkApplication();
		var transaction = Transactions.of(new byte[] { 13, 1, 19, 73 });
		String exceptionMessage = "rejected";
		when(app.getRepresentation(eq(transaction))).thenThrow(new RejectedTransactionException(exceptionMessage));
	
		class MyTestClient extends RemoteApplicationImpl {
	
			public MyTestClient() throws DeploymentException, IOException {
				super(URI, TIME_OUT);
			}
	
			@Override
			protected void onException(ExceptionMessage message) {
				if (RejectedTransactionException.class.isAssignableFrom(message.getExceptionClass()) && exceptionMessage.equals(message.getMessage()))
					semaphore.release();
			}
	
			private void sendGetRepresentation(Transaction transaction) throws ApplicationException {
				sendGetRepresentation(transaction, "id");
			}
		}
	
		try (var service = ApplicationServices.open(app, PORT); var client = new MyTestClient()) {
			client.sendGetRepresentation(transaction);
			assertTrue(semaphore.tryAcquire(1, 1, TimeUnit.SECONDS));
		}
	}

	@Test
	@DisplayName("if a getInitialStateId() request reaches the service, it yields the initial state id of the application")
	public void serviceGetInitialStateIdWorks() throws ApplicationException, TimeoutException, InterruptedException, DeploymentException, IOException {
		var semaphore = new Semaphore(0);
		var app = mkApplication();
		byte[] initialStateId = { 13, 1, 19, 73 };
		when(app.getInitialStateId()).thenReturn(initialStateId);

		class MyTestClient extends RemoteApplicationImpl {

			public MyTestClient() throws DeploymentException, IOException {
				super(URI, TIME_OUT);
			}

			@Override
			protected void onGetInitialStateIdResult(byte[] result) {
				if (Arrays.equals(initialStateId, result))
					semaphore.release();
			}

			private void sendGetInitialStateId() throws ApplicationException {
				sendGetInitialStateId("id");
			}
		}

		try (var service = ApplicationServices.open(app, PORT); var client = new MyTestClient()) {
			client.sendGetInitialStateId();
			assertTrue(semaphore.tryAcquire(1, 1, TimeUnit.SECONDS));
		}
	}

	@Test
	@DisplayName("if a getInitialStateId() request reaches the service and the application fails, it sends back an exception")
	public void serviceGetInitialStateApplicationExceptionWorks() throws RejectedTransactionException, ApplicationException, TimeoutException, InterruptedException, DeploymentException, IOException {
		var semaphore = new Semaphore(0);
		var app = mkApplication();
		String exceptionMessage = "failed";
		when(app.getInitialStateId()).thenThrow(new ApplicationException(exceptionMessage));
	
		class MyTestClient extends RemoteApplicationImpl {
	
			public MyTestClient() throws DeploymentException, IOException {
				super(URI, TIME_OUT);
			}
	
			@Override
			protected void onException(ExceptionMessage message) {
				if (ApplicationException.class.isAssignableFrom(message.getExceptionClass()) && exceptionMessage.equals(message.getMessage()))
					semaphore.release();
			}
	
			private void sendGetInitialStateId() throws ApplicationException {
				sendGetInitialStateId("id");
			}
		}
	
		try (var service = ApplicationServices.open(app, PORT); var client = new MyTestClient()) {
			client.sendGetInitialStateId();
			assertTrue(semaphore.tryAcquire(1, 1, TimeUnit.SECONDS));
		}
	}

	@Test
	@DisplayName("if a beginBlock() request reaches the service, it yields the group id of the transactions in the block")
	public void serviceBeginBlockWorks() throws UnknownStateException, ApplicationException, TimeoutException, InterruptedException, DeploymentException, IOException {
		var semaphore = new Semaphore(0);
		var app = mkApplication();
		long height = 42L;
		byte[] stateId = { 13, 1, 19, 73 };
		var when = LocalDateTime.of(1973, 1, 13, 0, 0);
		var groupId = 13;
		when(app.beginBlock(height, when, stateId)).thenReturn(groupId);

		class MyTestClient extends RemoteApplicationImpl {

			public MyTestClient() throws DeploymentException, IOException {
				super(URI, TIME_OUT);
			}

			@Override
			protected void onBeginBlockResult(int result) {
				if (groupId == result)
					semaphore.release();
			}

			private void sendBeginBlock() throws ApplicationException {
				sendBeginBlock(height, when, stateId, "id");
			}
		}

		try (var service = ApplicationServices.open(app, PORT); var client = new MyTestClient()) {
			client.sendBeginBlock();
			assertTrue(semaphore.tryAcquire(1, 1, TimeUnit.SECONDS));
		}
	}

	@Test
	@DisplayName("if a beginBlock() request reaches the service and the state id is unknown, it sends back an exception")
	public void serviceBeginBlockUnknownStateExceptionWorks() throws UnknownStateException, ApplicationException, TimeoutException, InterruptedException, DeploymentException, IOException {
		var semaphore = new Semaphore(0);
		var app = mkApplication();
		long height = 42L;
		byte[] stateId = { 13, 1, 19, 73 };
		var when = LocalDateTime.of(1973, 1, 13, 0, 0);
		String exceptionMessage = "unknown state";
		when(app.beginBlock(height, when, stateId)).thenThrow(new UnknownStateException(exceptionMessage));
	
		class MyTestClient extends RemoteApplicationImpl {
	
			public MyTestClient() throws DeploymentException, IOException {
				super(URI, TIME_OUT);
			}
	
			@Override
			protected void onException(ExceptionMessage message) {
				if (UnknownStateException.class.isAssignableFrom(message.getExceptionClass()) && exceptionMessage.equals(message.getMessage()))
					semaphore.release();
			}
	
			private void sendBeginBlock() throws ApplicationException {
				sendBeginBlock(height, when, stateId, "id");
			}
		}
	
		try (var service = ApplicationServices.open(app, PORT); var client = new MyTestClient()) {
			client.sendBeginBlock();
			assertTrue(semaphore.tryAcquire(1, 1, TimeUnit.SECONDS));
		}
	}

	@Test
	@DisplayName("if a deliverTransaction() request reaches the service, it delivers the transaction in the application")
	public void serviceDeliverTransactionWorks() throws UnknownGroupIdException, RejectedTransactionException, ApplicationException, TimeoutException, InterruptedException, DeploymentException, IOException {
		var semaphore = new Semaphore(0);
		var app = mkApplication();
		var transaction = Transactions.of(new byte[] { 13, 1, 19, 73 });
		var groupId = 13;
		doNothing().when(app).deliverTransaction(groupId, transaction);

		class MyTestClient extends RemoteApplicationImpl {

			public MyTestClient() throws DeploymentException, IOException {
				super(URI, TIME_OUT);
			}

			@Override
			protected void onDeliverTransactionResult() {
				semaphore.release();
			}

			private void sendDeliverTransaction() throws ApplicationException {
				sendDeliverTransaction(groupId, transaction, "id");
			}
		}

		try (var service = ApplicationServices.open(app, PORT); var client = new MyTestClient()) {
			client.sendDeliverTransaction();
			assertTrue(semaphore.tryAcquire(1, 1, TimeUnit.SECONDS));
		}
	}

	@Test
	@DisplayName("if a deliverTransaction() request reaches the service and the group id is unknown, it sends back an exception")
	public void serviceDeliverTransactionUnknownGroupIdExceptionWorks() throws UnknownGroupIdException, RejectedTransactionException, ApplicationException, TimeoutException, InterruptedException, DeploymentException, IOException {
		var semaphore = new Semaphore(0);
		var app = mkApplication();
		var transaction = Transactions.of(new byte[] { 13, 1, 19, 73 });
		var groupId = 13;
		var exceptionMessage = "unknown group id";
		doThrow(new UnknownGroupIdException(exceptionMessage)).when(app).deliverTransaction(groupId, transaction);
	
		class MyTestClient extends RemoteApplicationImpl {
	
			public MyTestClient() throws DeploymentException, IOException {
				super(URI, TIME_OUT);
			}
	
			@Override
			protected void onException(ExceptionMessage message) {
				if (UnknownGroupIdException.class.isAssignableFrom(message.getExceptionClass()) && exceptionMessage.equals(message.getMessage()))
					semaphore.release();
			}
	
			private void sendDeliverTransaction() throws ApplicationException {
				sendDeliverTransaction(groupId, transaction, "id");
			}
		}
	
		try (var service = ApplicationServices.open(app, PORT); var client = new MyTestClient()) {
			client.sendDeliverTransaction();
			assertTrue(semaphore.tryAcquire(1, 1, TimeUnit.SECONDS));
		}
	}

	@Test
	@DisplayName("if a deliverTransaction() request reaches the service and the transaction is rejected, it sends back an exception")
	public void serviceDeliverTransactionRejectedTransactionExceptionWorks() throws UnknownGroupIdException, RejectedTransactionException, ApplicationException, TimeoutException, InterruptedException, DeploymentException, IOException {
		var semaphore = new Semaphore(0);
		var app = mkApplication();
		var transaction = Transactions.of(new byte[] { 13, 1, 19, 73 });
		var groupId = 13;
		var exceptionMessage = "rejected";
		doThrow(new RejectedTransactionException(exceptionMessage)).when(app).deliverTransaction(groupId, transaction);
	
		class MyTestClient extends RemoteApplicationImpl {
	
			public MyTestClient() throws DeploymentException, IOException {
				super(URI, TIME_OUT);
			}
	
			@Override
			protected void onException(ExceptionMessage message) {
				if (RejectedTransactionException.class.isAssignableFrom(message.getExceptionClass()) && exceptionMessage.equals(message.getMessage()))
					semaphore.release();
			}
	
			private void sendDeliverTransaction() throws ApplicationException {
				sendDeliverTransaction(groupId, transaction, "id");
			}
		}
	
		try (var service = ApplicationServices.open(app, PORT); var client = new MyTestClient()) {
			client.sendDeliverTransaction();
			assertTrue(semaphore.tryAcquire(1, 1, TimeUnit.SECONDS));
		}
	}

	@Test
	@DisplayName("if an endBlock() request reaches the service, it yeilds the state identifier ayt the end of the block")
	public void serviceEndBlockWorks() throws UnknownGroupIdException, ApplicationException, TimeoutException, InterruptedException, DeploymentException, IOException, NoSuchAlgorithmException, InvalidKeyException, SignatureException {
		var semaphore = new Semaphore(0);
		var app = mkApplication();
		var groupId = 13;
		var hashing = HashingAlgorithms.shabal256();
		var value = new byte[hashing.length()];
		for (int pos = 0; pos < value.length; pos++)
			value[pos] = (byte) pos;
		var ed25519 = SignatureAlgorithms.ed25519();
		var plotKeyPair = ed25519.getKeyPair();
		var prolog = Prologs.of("octopus", ed25519, ed25519.getKeyPair().getPublic(), ed25519, plotKeyPair.getPublic(), new byte[0]);
		var deadline = Deadlines.of(prolog, 13, value, 11, new byte[] { 90, 91, 92 }, hashing, plotKeyPair.getPrivate());
		byte[] finalStateId = { 25, 12, 20, 24 };
		when(app.endBlock(groupId, deadline)).thenReturn(finalStateId);

		class MyTestClient extends RemoteApplicationImpl {

			public MyTestClient() throws DeploymentException, IOException {
				super(URI, TIME_OUT);
			}

			@Override
			protected void onEndBlockResult(byte[] stateId) {
				if (Arrays.equals(finalStateId, stateId))
					semaphore.release();
			}

			private void sendEndBlock() throws ApplicationException {
				sendEndBlock(groupId, deadline, "id");
			}
		}

		try (var service = ApplicationServices.open(app, PORT); var client = new MyTestClient()) {
			client.sendEndBlock();
			assertTrue(semaphore.tryAcquire(1, 1, TimeUnit.SECONDS));
		}
	}

	@Test
	@DisplayName("if an endBlock() request reaches the service and the group id is unknown, it sends back an exception")
	public void serviceEndBlockUnknownGroupIdExceptionWorks() throws UnknownGroupIdException, ApplicationException, TimeoutException, InterruptedException, DeploymentException, IOException, NoSuchAlgorithmException, InvalidKeyException, SignatureException {
		var semaphore = new Semaphore(0);
		var app = mkApplication();
		var groupId = 13;
		var hashing = HashingAlgorithms.shabal256();
		var value = new byte[hashing.length()];
		for (int pos = 0; pos < value.length; pos++)
			value[pos] = (byte) pos;
		var ed25519 = SignatureAlgorithms.ed25519();
		var plotKeyPair = ed25519.getKeyPair();
		var prolog = Prologs.of("octopus", ed25519, ed25519.getKeyPair().getPublic(), ed25519, plotKeyPair.getPublic(), new byte[0]);
		var deadline = Deadlines.of(prolog, 13, value, 11, new byte[] { 90, 91, 92 }, hashing, plotKeyPair.getPrivate());
		var exceptionMessage = "unknown group id";
		when(app.endBlock(groupId, deadline)).thenThrow(new UnknownGroupIdException(exceptionMessage));
	
		class MyTestClient extends RemoteApplicationImpl {
	
			public MyTestClient() throws DeploymentException, IOException {
				super(URI, TIME_OUT);
			}
	
			@Override
			protected void onException(ExceptionMessage message) {
				if (UnknownGroupIdException.class.isAssignableFrom(message.getExceptionClass()) && exceptionMessage.equals(message.getMessage()))
					semaphore.release();
			}
	
			private void sendEndBlock() throws ApplicationException {
				sendEndBlock(groupId, deadline, "id");
			}
		}
	
		try (var service = ApplicationServices.open(app, PORT); var client = new MyTestClient()) {
			client.sendEndBlock();
			assertTrue(semaphore.tryAcquire(1, 1, TimeUnit.SECONDS));
		}
	}

	@Test
	@DisplayName("if a commitBlock() request reaches the service, the block gets committed")
	public void serviceCommitBlockWorks() throws UnknownGroupIdException, ApplicationException, TimeoutException, InterruptedException, DeploymentException, IOException {
		var semaphore = new Semaphore(0);
		var app = mkApplication();
		var groupId = 13;
		doNothing().when(app).commitBlock(groupId);

		class MyTestClient extends RemoteApplicationImpl {

			public MyTestClient() throws DeploymentException, IOException {
				super(URI, TIME_OUT);
			}

			@Override
			protected void onCommitBlockResult() {
				semaphore.release();
			}

			private void sendCommitBlock() throws ApplicationException {
				sendCommitBlock(groupId, "id");
			}
		}

		try (var service = ApplicationServices.open(app, PORT); var client = new MyTestClient()) {
			client.sendCommitBlock();
			assertTrue(semaphore.tryAcquire(1, 1, TimeUnit.SECONDS));
		}
	}

	@Test
	@DisplayName("if a commitBlock() request reaches the service and the group id is unknown, it sends back an exception")
	public void serviceCommitBlockUnknownGroupIdExceptionWorks() throws UnknownGroupIdException, ApplicationException, TimeoutException, InterruptedException, DeploymentException, IOException {
		var semaphore = new Semaphore(0);
		var app = mkApplication();
		var groupId = 13;
		String exceptionMessage = "unknown group id";
		doThrow(new UnknownGroupIdException(exceptionMessage)).when(app).commitBlock(groupId);
	
		class MyTestClient extends RemoteApplicationImpl {
	
			public MyTestClient() throws DeploymentException, IOException {
				super(URI, TIME_OUT);
			}
	
			@Override
			protected void onException(ExceptionMessage message) {
				if (ID.equals(message.getId()) && UnknownGroupIdException.class.isAssignableFrom(message.getExceptionClass()) && exceptionMessage.equals(message.getMessage()))
					semaphore.release();
			}
	
			private void sendCommitBlock() throws ApplicationException {
				sendCommitBlock(groupId, ID);
			}
		}
	
		try (var service = ApplicationServices.open(app, PORT); var client = new MyTestClient()) {
			client.sendCommitBlock();
			assertTrue(semaphore.tryAcquire(1, 1, TimeUnit.SECONDS));
		}
	}

	@Test
	@DisplayName("if an abortBlock() request reaches the service, the block gets aborted")
	public void serviceAbortBlockWorks() throws UnknownGroupIdException, ApplicationException, TimeoutException, InterruptedException, DeploymentException, IOException {
		var semaphore = new Semaphore(0);
		var app = mkApplication();
		var groupId = 13;
		doNothing().when(app).abortBlock(groupId);

		class MyTestClient extends RemoteApplicationImpl {

			public MyTestClient() throws DeploymentException, IOException {
				super(URI, TIME_OUT);
			}

			@Override
			protected void onAbortBlockResult() {
				semaphore.release();
			}

			private void sendAbortBlock() throws ApplicationException {
				sendAbortBlock(groupId, "id");
			}
		}

		try (var service = ApplicationServices.open(app, PORT); var client = new MyTestClient()) {
			client.sendAbortBlock();
			assertTrue(semaphore.tryAcquire(1, 1, TimeUnit.SECONDS));
		}
	}

	@Test
	@DisplayName("if an abortBlock() request reaches the service and the group id is unknown, it sends back an exception")
	public void serviceAbortBlockUnknownGroupIdExceptionWorks() throws UnknownGroupIdException, ApplicationException, TimeoutException, InterruptedException, DeploymentException, IOException {
		var semaphore = new Semaphore(0);
		var app = mkApplication();
		var groupId = 13;
		String exceptionMessage = "unknown group id";
		doThrow(new UnknownGroupIdException(exceptionMessage)).when(app).abortBlock(groupId);
	
		class MyTestClient extends RemoteApplicationImpl {
	
			public MyTestClient() throws DeploymentException, IOException {
				super(URI, TIME_OUT);
			}
	
			@Override
			protected void onException(ExceptionMessage message) {
				if (ID.equals(message.getId()) && UnknownGroupIdException.class.isAssignableFrom(message.getExceptionClass()) && exceptionMessage.equals(message.getMessage()))
					semaphore.release();
			}
	
			private void sendAbortBlock() throws ApplicationException {
				sendAbortBlock(groupId, ID);
			}
		}
	
		try (var service = ApplicationServices.open(app, PORT); var client = new MyTestClient()) {
			client.sendAbortBlock();
			assertTrue(semaphore.tryAcquire(1, 1, TimeUnit.SECONDS));
		}
	}
}