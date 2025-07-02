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

import static io.hotmoka.crypto.HashingAlgorithms.sha256;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.net.URI;
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
import io.hotmoka.websockets.api.FailedDeploymentException;
import io.hotmoka.websockets.beans.api.ExceptionMessage;
import io.mokamint.application.api.Application;
import io.mokamint.application.api.ClosedApplicationException;
import io.mokamint.application.api.UnknownGroupIdException;
import io.mokamint.application.api.UnknownStateException;
import io.mokamint.application.messages.api.AbortBlockResultMessage;
import io.mokamint.application.messages.api.BeginBlockResultMessage;
import io.mokamint.application.messages.api.CheckPrologExtraResultMessage;
import io.mokamint.application.messages.api.CheckTransactionResultMessage;
import io.mokamint.application.messages.api.CommitBlockResultMessage;
import io.mokamint.application.messages.api.DeliverTransactionResultMessage;
import io.mokamint.application.messages.api.EndBlockResultMessage;
import io.mokamint.application.messages.api.GetInitialStateIdResultMessage;
import io.mokamint.application.messages.api.GetPriorityResultMessage;
import io.mokamint.application.messages.api.GetRepresentationResultMessage;
import io.mokamint.application.messages.api.KeepFromResultMessage;
import io.mokamint.application.remote.RemoteApplications;
import io.mokamint.application.remote.internal.RemoteApplicationImpl;
import io.mokamint.application.service.ApplicationServices;
import io.mokamint.node.Transactions;
import io.mokamint.node.api.Transaction;
import io.mokamint.node.api.TransactionRejectedException;
import io.mokamint.nonce.Challenges;
import io.mokamint.nonce.Deadlines;
import io.mokamint.nonce.Prologs;

public class ApplicationServiceTests extends AbstractLoggedTests {
	private final static int PORT = 8030;
	private final static URI URI = java.net.URI.create("ws://localhost:" + PORT);
	private final static int TIME_OUT = 2000;
	private final static String ID = "id";

	private Application mkApplication() {
		return mock();
	}

	@Test
	@DisplayName("if a checkPrologExtra() request reaches the service, it sends back the result of the check")
	public void serviceCheckPrologExtraWorks() throws Exception {
		var semaphore = new Semaphore(0);
		var app = mkApplication();
		byte[] extra = { 13, 1, 19, 73 };
		when(app.checkPrologExtra(eq(extra))).thenReturn(true);

		class MyTestClient extends RemoteApplicationImpl {

			public MyTestClient() throws FailedDeploymentException {
				super(URI, TIME_OUT);
			}

			@Override
			protected void onCheckPrologExtraResult(CheckPrologExtraResultMessage message) {
				if (ID.equals(message.getId()) && message.get())
					semaphore.release();
			}

			private void sendCheckPrologExtra() {
				sendCheckPrologExtra(extra, ID);
			}
		}

		try (var service = ApplicationServices.open(app, PORT); var client = new MyTestClient()) {
			client.sendCheckPrologExtra();
			assertTrue(semaphore.tryAcquire(1, 1, TimeUnit.SECONDS));
		}
	}

	@Test
	@DisplayName("if a checkTransaction() request reaches the service, it checks the transaction")
	public void serviceCheckTransactionWorks() throws Exception {
		var semaphore = new Semaphore(0);
		var app = mkApplication();
		var transaction = Transactions.of(new byte[] { 13, 1, 19, 73 });
		doNothing().when(app).checkTransaction(eq(transaction));

		class MyTestClient extends RemoteApplicationImpl {

			public MyTestClient() throws FailedDeploymentException {
				super(URI, TIME_OUT);
			}

			@Override
			protected void onCheckTransactionResult(CheckTransactionResultMessage message) {
				if (ID.equals(message.getId()))
					semaphore.release();
			}

			private void sendCheckTransaction() {
				sendCheckTransaction(transaction, ID);
			}
		}

		try (var service = ApplicationServices.open(app, PORT); var client = new MyTestClient()) {
			client.sendCheckTransaction();
			assertTrue(semaphore.tryAcquire(1, 1, TimeUnit.SECONDS));
		}
	}

	@Test
	@DisplayName("if a checkTransaction() request reaches the service and the transaction is rejected, it sends back an exception")
	public void serviceCheckTransactionRejectedTransactionWorks() throws Exception {
		var semaphore = new Semaphore(0);
		var app = mkApplication();
		var transaction = Transactions.of(new byte[] { 13, 1, 19, 73 });
		var exceptionMessage = "rejected";
		doThrow(new TransactionRejectedException(exceptionMessage)).when(app).checkTransaction(eq(transaction));
	
		class MyTestClient extends RemoteApplicationImpl {
	
			public MyTestClient() throws FailedDeploymentException {
				super(URI, TIME_OUT);
			}
	
			@Override
			protected void onException(ExceptionMessage message) {
				if (ID.equals(message.getId()) && TransactionRejectedException.class.isAssignableFrom(message.getExceptionClass()) && exceptionMessage.equals(message.getMessage().get()))
					semaphore.release();
			}
	
			private void sendCheckTransaction(Transaction transaction) {
				sendCheckTransaction(transaction, ID);
			}
		}
	
		try (var service = ApplicationServices.open(app, PORT); var client = new MyTestClient()) {
			client.sendCheckTransaction(transaction);
			assertTrue(semaphore.tryAcquire(1, 1, TimeUnit.SECONDS));
		}
	}

	@Test
	@DisplayName("if a getPriority() request reaches the service, it yields the priority of the transaction")
	public void serviceGetPriorityWorks() throws Exception {
		var semaphore = new Semaphore(0);
		var app = mkApplication();
		var transaction = Transactions.of(new byte[] { 13, 1, 19, 73 });
		when(app.getPriority(eq(transaction))).thenReturn(42L);

		class MyTestClient extends RemoteApplicationImpl {

			public MyTestClient() throws FailedDeploymentException {
				super(URI, TIME_OUT);
			}

			@Override
			protected void onGetPriorityResult(GetPriorityResultMessage message) {
				if (ID.equals(message.getId()) && message.get() == 42L)
					semaphore.release();
			}

			private void sendGetPriority() {
				sendGetPriority(transaction, ID);
			}
		}

		try (var service = ApplicationServices.open(app, PORT); var client = new MyTestClient()) {
			client.sendGetPriority();
			assertTrue(semaphore.tryAcquire(1, 1, TimeUnit.SECONDS));
		}
	}

	@Test
	@DisplayName("if a getPriority() request reaches the service and the transaction is rejected, it sends back an exception")
	public void serviceGetPriorityRejectedTransactionWorks() throws Exception {
		var semaphore = new Semaphore(0);
		var app = mkApplication();
		var transaction = Transactions.of(new byte[] { 13, 1, 19, 73 });
		String exceptionMessage = "rejected";
		when(app.getPriority(eq(transaction))).thenThrow(new TransactionRejectedException(exceptionMessage));
	
		class MyTestClient extends RemoteApplicationImpl {
	
			public MyTestClient() throws FailedDeploymentException {
				super(URI, TIME_OUT);
			}
	
			@Override
			protected void onException(ExceptionMessage message) {
				if (ID.equals(message.getId()) && TransactionRejectedException.class.isAssignableFrom(message.getExceptionClass()) && exceptionMessage.equals(message.getMessage().get()))
					semaphore.release();
			}
	
			private void sendGetPriority(Transaction transaction) {
				sendGetPriority(transaction, ID);
			}
		}
	
		try (var service = ApplicationServices.open(app, PORT); var client = new MyTestClient()) {
			client.sendGetPriority(transaction);
			assertTrue(semaphore.tryAcquire(1, 1, TimeUnit.SECONDS));
		}
	}

	@Test
	@DisplayName("if a getRepresentation() request reaches the service, it yields the representation of the transaction")
	public void serviceGetRepresentationWorks() throws Exception {
		var semaphore = new Semaphore(0);
		var app = mkApplication();
		var transaction = Transactions.of(new byte[] { 13, 1, 19, 73 });
		var representation = "this is the wonderful representation";
		when(app.getRepresentation(eq(transaction))).thenReturn(representation);

		class MyTestClient extends RemoteApplicationImpl {

			public MyTestClient() throws FailedDeploymentException {
				super(URI, TIME_OUT);
			}

			@Override
			protected void onGetRepresentationResult(GetRepresentationResultMessage message) {
				if (ID.equals(message.getId()) && representation.equals(message.get()))
					semaphore.release();
			}

			private void sendGetRepresentation() {
				sendGetRepresentation(transaction, ID);
			}
		}

		try (var service = ApplicationServices.open(app, PORT); var client = new MyTestClient()) {
			client.sendGetRepresentation();
			assertTrue(semaphore.tryAcquire(1, 1, TimeUnit.SECONDS));
		}
	}

	@Test
	@DisplayName("if a getRepresentation() request reaches the service and the transaction is rejected, it sends back an exception")
	public void serviceGetRepresentationRejectedTransactionWorks() throws Exception {
		var semaphore = new Semaphore(0);
		var app = mkApplication();
		var transaction = Transactions.of(new byte[] { 13, 1, 19, 73 });
		String exceptionMessage = "rejected";
		when(app.getRepresentation(eq(transaction))).thenThrow(new TransactionRejectedException(exceptionMessage));
	
		class MyTestClient extends RemoteApplicationImpl {
	
			public MyTestClient() throws FailedDeploymentException {
				super(URI, TIME_OUT);
			}
	
			@Override
			protected void onException(ExceptionMessage message) {
				if (ID.equals(message.getId()) && TransactionRejectedException.class.isAssignableFrom(message.getExceptionClass()) && exceptionMessage.equals(message.getMessage().get()))
					semaphore.release();
			}
	
			private void sendGetRepresentation(Transaction transaction) {
				sendGetRepresentation(transaction, ID);
			}
		}
	
		try (var service = ApplicationServices.open(app, PORT); var client = new MyTestClient()) {
			client.sendGetRepresentation(transaction);
			assertTrue(semaphore.tryAcquire(1, 1, TimeUnit.SECONDS));
		}
	}

	@Test
	@DisplayName("if a getInitialStateId() request reaches the service, it yields the initial state id of the application")
	public void serviceGetInitialStateIdWorks() throws Exception {
		var semaphore = new Semaphore(0);
		var app = mkApplication();
		byte[] initialStateId = { 13, 1, 19, 73 };
		when(app.getInitialStateId()).thenReturn(initialStateId);

		class MyTestClient extends RemoteApplicationImpl {

			public MyTestClient() throws FailedDeploymentException {
				super(URI, TIME_OUT);
			}

			@Override
			protected void onGetInitialStateIdResult(GetInitialStateIdResultMessage message) {
				if (ID.equals(message.getId()) && Arrays.equals(initialStateId, message.get()))
					semaphore.release();
			}

			private void sendGetInitialStateId() {
				sendGetInitialStateId(ID);
			}
		}

		try (var service = ApplicationServices.open(app, PORT); var client = new MyTestClient()) {
			client.sendGetInitialStateId();
			assertTrue(semaphore.tryAcquire(1, 1, TimeUnit.SECONDS));
		}
	}

	@Test
	@DisplayName("if a getInitialStateId() request reaches the service and the application is closed, it sends back an exception")
	public void serviceGetInitialStateApplicationExceptionWorks() throws Exception {
		var app = mkApplication();
		when(app.getInitialStateId()).thenThrow(new ClosedApplicationException());
	
		try (var service = ApplicationServices.open(app, PORT); var client = RemoteApplications.of(URI, TIME_OUT)) {
			assertThrows(TimeoutException.class, client::getInitialStateId);
		}
	}

	@Test
	@DisplayName("if a beginBlock() request reaches the service, it yields the group id of the transactions in the block")
	public void serviceBeginBlockWorks() throws Exception {
		var semaphore = new Semaphore(0);
		var app = mkApplication();
		long height = 42L;
		byte[] stateId = { 13, 1, 19, 73 };
		var when = LocalDateTime.of(1973, 1, 13, 0, 0);
		var groupId = 13;
		when(app.beginBlock(height, when, stateId)).thenReturn(groupId);

		class MyTestClient extends RemoteApplicationImpl {

			public MyTestClient() throws FailedDeploymentException {
				super(URI, TIME_OUT);
			}

			@Override
			protected void onBeginBlockResult(BeginBlockResultMessage message) {
				if (ID.equals(message.getId()) && groupId == message.get().intValue())
					semaphore.release();
			}

			private void sendBeginBlock() {
				sendBeginBlock(height, when, stateId, ID);
			}
		}

		try (var service = ApplicationServices.open(app, PORT); var client = new MyTestClient()) {
			client.sendBeginBlock();
			assertTrue(semaphore.tryAcquire(1, 1, TimeUnit.SECONDS));
		}
	}

	@Test
	@DisplayName("if a beginBlock() request reaches the service and the state id is unknown, it sends back an exception")
	public void serviceBeginBlockUnknownStateExceptionWorks() throws Exception {
		var semaphore = new Semaphore(0);
		var app = mkApplication();
		long height = 42L;
		byte[] stateId = { 13, 1, 19, 73 };
		var when = LocalDateTime.of(1973, 1, 13, 0, 0);
		String exceptionMessage = "unknown state";
		when(app.beginBlock(height, when, stateId)).thenThrow(new UnknownStateException(exceptionMessage));
	
		class MyTestClient extends RemoteApplicationImpl {
	
			public MyTestClient() throws FailedDeploymentException {
				super(URI, TIME_OUT);
			}
	
			@Override
			protected void onException(ExceptionMessage message) {
				if (ID.equals(message.getId()) && UnknownStateException.class.isAssignableFrom(message.getExceptionClass()) && exceptionMessage.equals(message.getMessage().get()))
					semaphore.release();
			}
	
			private void sendBeginBlock() {
				sendBeginBlock(height, when, stateId, ID);
			}
		}
	
		try (var service = ApplicationServices.open(app, PORT); var client = new MyTestClient()) {
			client.sendBeginBlock();
			assertTrue(semaphore.tryAcquire(1, 1, TimeUnit.SECONDS));
		}
	}

	@Test
	@DisplayName("if a deliverTransaction() request reaches the service, it delivers the transaction in the application")
	public void serviceDeliverTransactionWorks() throws Exception {
		var semaphore = new Semaphore(0);
		var app = mkApplication();
		var transaction = Transactions.of(new byte[] { 13, 1, 19, 73 });
		var groupId = 13;
		doNothing().when(app).deliverTransaction(groupId, transaction);

		class MyTestClient extends RemoteApplicationImpl {

			public MyTestClient() throws FailedDeploymentException {
				super(URI, TIME_OUT);
			}

			@Override
			protected void onDeliverTransactionResult(DeliverTransactionResultMessage message) {
				if (ID.equals(message.getId()))
					semaphore.release();
			}

			private void sendDeliverTransaction() {
				sendDeliverTransaction(groupId, transaction, ID);
			}
		}

		try (var service = ApplicationServices.open(app, PORT); var client = new MyTestClient()) {
			client.sendDeliverTransaction();
			assertTrue(semaphore.tryAcquire(1, 1, TimeUnit.SECONDS));
		}
	}

	@Test
	@DisplayName("if a deliverTransaction() request reaches the service and the group id is unknown, it sends back an exception")
	public void serviceDeliverTransactionUnknownGroupIdExceptionWorks() throws Exception {
		var semaphore = new Semaphore(0);
		var app = mkApplication();
		var transaction = Transactions.of(new byte[] { 13, 1, 19, 73 });
		var groupId = 13;
		var exceptionMessage = "unknown group id";
		doThrow(new UnknownGroupIdException(exceptionMessage)).when(app).deliverTransaction(groupId, transaction);
	
		class MyTestClient extends RemoteApplicationImpl {
	
			public MyTestClient() throws FailedDeploymentException {
				super(URI, TIME_OUT);
			}
	
			@Override
			protected void onException(ExceptionMessage message) {
				if (ID.equals(message.getId()) && UnknownGroupIdException.class.isAssignableFrom(message.getExceptionClass()) && exceptionMessage.equals(message.getMessage().get()))
					semaphore.release();
			}
	
			private void sendDeliverTransaction() {
				sendDeliverTransaction(groupId, transaction, ID);
			}
		}
	
		try (var service = ApplicationServices.open(app, PORT); var client = new MyTestClient()) {
			client.sendDeliverTransaction();
			assertTrue(semaphore.tryAcquire(1, 1, TimeUnit.SECONDS));
		}
	}

	@Test
	@DisplayName("if a deliverTransaction() request reaches the service and the transaction is rejected, it sends back an exception")
	public void serviceDeliverTransactionRejectedTransactionExceptionWorks() throws Exception {
		var semaphore = new Semaphore(0);
		var app = mkApplication();
		var transaction = Transactions.of(new byte[] { 13, 1, 19, 73 });
		var groupId = 13;
		var exceptionMessage = "rejected";
		doThrow(new TransactionRejectedException(exceptionMessage)).when(app).deliverTransaction(groupId, transaction);
	
		class MyTestClient extends RemoteApplicationImpl {
	
			public MyTestClient() throws FailedDeploymentException {
				super(URI, TIME_OUT);
			}
	
			@Override
			protected void onException(ExceptionMessage message) {
				if (ID.equals(message.getId()) && TransactionRejectedException.class.isAssignableFrom(message.getExceptionClass()) && exceptionMessage.equals(message.getMessage().get()))
					semaphore.release();
			}
	
			private void sendDeliverTransaction() {
				sendDeliverTransaction(groupId, transaction, ID);
			}
		}
	
		try (var service = ApplicationServices.open(app, PORT); var client = new MyTestClient()) {
			client.sendDeliverTransaction();
			assertTrue(semaphore.tryAcquire(1, 1, TimeUnit.SECONDS));
		}
	}

	@Test
	@DisplayName("if an endBlock() request reaches the service, it yields the state identifier at the end of the block")
	public void serviceEndBlockWorks() throws Exception {
		var semaphore = new Semaphore(0);
		var app = mkApplication();
		var groupId = 13;
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
		var deadline = Deadlines.of(prolog, 13, value, Challenges.of(11, generationSignature, hashingForDeadlines, hashingForGenerations), plotKeyPair.getPrivate());
		byte[] finalStateId = { 25, 12, 20, 24 };
		when(app.endBlock(groupId, deadline)).thenReturn(finalStateId);

		class MyTestClient extends RemoteApplicationImpl {

			public MyTestClient() throws FailedDeploymentException {
				super(URI, TIME_OUT);
			}

			@Override
			protected void onEndBlockResult(EndBlockResultMessage message) {
				if (ID.equals(message.getId()) && Arrays.equals(finalStateId, message.get()))
					semaphore.release();
			}

			private void sendEndBlock() {
				sendEndBlock(groupId, deadline, ID);
			}
		}

		try (var service = ApplicationServices.open(app, PORT); var client = new MyTestClient()) {
			client.sendEndBlock();
			assertTrue(semaphore.tryAcquire(1, 1, TimeUnit.SECONDS));
		}
	}

	@Test
	@DisplayName("if an endBlock() request reaches the service and the group id is unknown, it sends back an exception")
	public void serviceEndBlockUnknownGroupIdExceptionWorks() throws Exception {
		var semaphore = new Semaphore(0);
		var app = mkApplication();
		var groupId = 13;
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
		var deadline = Deadlines.of(prolog, 13, value, Challenges.of(11, generationSignature, hashingForDeadlines, hashingForGenerations), plotKeyPair.getPrivate());
		var exceptionMessage = "unknown group id";
		when(app.endBlock(groupId, deadline)).thenThrow(new UnknownGroupIdException(exceptionMessage));
	
		class MyTestClient extends RemoteApplicationImpl {
	
			public MyTestClient() throws FailedDeploymentException {
				super(URI, TIME_OUT);
			}
	
			@Override
			protected void onException(ExceptionMessage message) {
				if (ID.equals(message.getId()) && UnknownGroupIdException.class.isAssignableFrom(message.getExceptionClass()) && exceptionMessage.equals(message.getMessage().get()))
					semaphore.release();
			}
	
			private void sendEndBlock() {
				sendEndBlock(groupId, deadline, ID);
			}
		}
	
		try (var service = ApplicationServices.open(app, PORT); var client = new MyTestClient()) {
			client.sendEndBlock();
			assertTrue(semaphore.tryAcquire(1, 1, TimeUnit.SECONDS));
		}
	}

	@Test
	@DisplayName("if a commitBlock() request reaches the service, the block gets committed")
	public void serviceCommitBlockWorks() throws Exception {
		var semaphore = new Semaphore(0);
		var app = mkApplication();
		var groupId = 13;
		doNothing().when(app).commitBlock(groupId);

		class MyTestClient extends RemoteApplicationImpl {

			public MyTestClient() throws FailedDeploymentException {
				super(URI, TIME_OUT);
			}

			@Override
			protected void onCommitBlockResult(CommitBlockResultMessage cbrm) {
				if (ID.equals(cbrm.getId()))
					semaphore.release();
			}

			private void sendCommitBlock() {
				sendCommitBlock(groupId, ID);
			}
		}

		try (var service = ApplicationServices.open(app, PORT); var client = new MyTestClient()) {
			client.sendCommitBlock();
			assertTrue(semaphore.tryAcquire(1, 1, TimeUnit.SECONDS));
		}
	}

	@Test
	@DisplayName("if a commitBlock() request reaches the service and the group id is unknown, it sends back an exception")
	public void serviceCommitBlockUnknownGroupIdExceptionWorks() throws Exception {
		var semaphore = new Semaphore(0);
		var app = mkApplication();
		var groupId = 13;
		String exceptionMessage = "unknown group id";
		doThrow(new UnknownGroupIdException(exceptionMessage)).when(app).commitBlock(groupId);
	
		class MyTestClient extends RemoteApplicationImpl {
	
			public MyTestClient() throws FailedDeploymentException {
				super(URI, TIME_OUT);
			}
	
			@Override
			protected void onException(ExceptionMessage message) {
				if (ID.equals(message.getId()) && UnknownGroupIdException.class.isAssignableFrom(message.getExceptionClass()) && exceptionMessage.equals(message.getMessage().get()))
					semaphore.release();
			}
	
			private void sendCommitBlock() {
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
	public void serviceAbortBlockWorks() throws Exception {
		var semaphore = new Semaphore(0);
		var app = mkApplication();
		var groupId = 13;
		doNothing().when(app).abortBlock(groupId);

		class MyTestClient extends RemoteApplicationImpl {

			public MyTestClient() throws FailedDeploymentException {
				super(URI, TIME_OUT);
			}

			@Override
			protected void onAbortBlockResult(AbortBlockResultMessage message) {
				if (ID.equals(message.getId()))
					semaphore.release();
			}

			private void sendAbortBlock() {
				sendAbortBlock(groupId, ID);
			}
		}

		try (var service = ApplicationServices.open(app, PORT); var client = new MyTestClient()) {
			client.sendAbortBlock();
			assertTrue(semaphore.tryAcquire(1, 1, TimeUnit.SECONDS));
		}
	}

	@Test
	@DisplayName("if an abortBlock() request reaches the service and the group id is unknown, it sends back an exception")
	public void serviceAbortBlockUnknownGroupIdExceptionWorks() throws Exception {
		var semaphore = new Semaphore(0);
		var app = mkApplication();
		var groupId = 13;
		String exceptionMessage = "unknown group id";
		doThrow(new UnknownGroupIdException(exceptionMessage)).when(app).abortBlock(groupId);
	
		class MyTestClient extends RemoteApplicationImpl {
	
			public MyTestClient() throws FailedDeploymentException {
				super(URI, TIME_OUT);
			}
	
			@Override
			protected void onException(ExceptionMessage message) {
				if (ID.equals(message.getId()) && UnknownGroupIdException.class.isAssignableFrom(message.getExceptionClass()) && exceptionMessage.equals(message.getMessage().get()))
					semaphore.release();
			}
	
			private void sendAbortBlock() {
				sendAbortBlock(groupId, ID);
			}
		}
	
		try (var service = ApplicationServices.open(app, PORT); var client = new MyTestClient()) {
			client.sendAbortBlock();
			assertTrue(semaphore.tryAcquire(1, 1, TimeUnit.SECONDS));
		}
	}

	@Test
	@DisplayName("if a keepFrom() request reaches the service, the application gets informed")
	public void serviceKeepFromWorks() throws Exception {
		var semaphore = new Semaphore(0);
		var app = mkApplication();
		var start = LocalDateTime.now();
		doNothing().when(app).keepFrom(start);

		class MyTestClient extends RemoteApplicationImpl {

			public MyTestClient() throws FailedDeploymentException {
				super(URI, TIME_OUT);
			}

			@Override
			protected void onKeepFromResult(KeepFromResultMessage message) {
				if (ID.equals(message.getId()))
					semaphore.release();
			}

			private void sendKeepFrom() {
				sendKeepFrom(start, ID);
			}
		}

		try (var service = ApplicationServices.open(app, PORT); var client = new MyTestClient()) {
			client.sendKeepFrom();
			assertTrue(semaphore.tryAcquire(1, 1, TimeUnit.SECONDS));
		}
	}
}