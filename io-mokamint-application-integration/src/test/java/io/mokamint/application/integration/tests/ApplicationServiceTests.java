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
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import io.hotmoka.testing.AbstractLoggedTests;
import io.hotmoka.websockets.beans.api.ExceptionMessage;
import io.mokamint.application.api.Application;
import io.mokamint.application.api.ApplicationException;
import io.mokamint.application.api.UnknownStateException;
import io.mokamint.application.remote.internal.RemoteApplicationImpl;
import io.mokamint.application.service.ApplicationServices;
import io.mokamint.node.Transactions;
import io.mokamint.node.api.RejectedTransactionException;
import io.mokamint.node.api.Transaction;
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
		doThrow(RejectedTransactionException.class).when(app).checkTransaction(eq(transaction));
	
		class MyTestClient extends RemoteApplicationImpl {
	
			public MyTestClient() throws DeploymentException, IOException {
				super(URI, TIME_OUT);
			}
	
			@Override
			protected void onException(ExceptionMessage message) {
				if (RejectedTransactionException.class.isAssignableFrom(message.getExceptionClass()))
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
}