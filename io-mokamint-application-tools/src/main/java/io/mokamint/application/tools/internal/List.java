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

package io.mokamint.application.tools.internal;

import java.time.LocalDateTime;
import java.util.ServiceLoader;
import java.util.ServiceLoader.Provider;
import java.util.concurrent.TimeoutException;
import java.util.logging.Logger;

import io.hotmoka.closeables.api.OnCloseHandler;
import io.mokamint.application.api.Application;
import io.mokamint.application.api.ApplicationException;
import io.mokamint.application.api.Description;
import io.mokamint.application.api.Name;
import io.mokamint.application.api.UnknownGroupIdException;
import io.mokamint.application.api.UnknownStateException;
import io.mokamint.node.api.RejectedTransactionException;
import io.mokamint.node.api.Transaction;
import io.mokamint.nonce.api.Deadline;
import io.mokamint.tools.AbstractCommand;
import io.mokamint.tools.CommandException;
import picocli.CommandLine.Command;

@Command(name = "ls",
	description = "List the available applications.",
	showDefaultValues = true)
public class List extends AbstractCommand {

	private final static Logger LOGGER = Logger.getLogger(List.class.getName());

	@Override
	protected void execute() throws CommandException {
		ServiceLoader<Application> serviceLoader = ServiceLoader.load(Application.class);
		serviceLoader.stream()
			.forEach(this::describe);
	}

	private void describe(Provider<Application> provider) {
		var type = provider.type();
		String className = type.getName();
		Name annName = type.getAnnotation(Name.class);
		String name = annName != null ? annName.value() : "---";
		Description annDescription = type.getAnnotation(Description.class);
		String description = annDescription != null ? annDescription.value() : "---";
		System.out.println(name + "  " + className + "  " + description);
	}

	@Name("App1")
	@Description("a first simple application")
	public static class App1 implements Application {

		@Override
		public void addOnCloseHandler(OnCloseHandler handler) {
			// TODO Auto-generated method stub
			
		}

		@Override
		public void removeOnCloseHandler(OnCloseHandler handler) {
			// TODO Auto-generated method stub
			
		}

		@Override
		public boolean checkPrologExtra(byte[] extra)
				throws ApplicationException, TimeoutException, InterruptedException {
			// TODO Auto-generated method stub
			return false;
		}

		@Override
		public void checkTransaction(Transaction transaction)
				throws RejectedTransactionException, ApplicationException, TimeoutException, InterruptedException {
			// TODO Auto-generated method stub
			
		}

		@Override
		public long getPriority(Transaction transaction)
				throws RejectedTransactionException, ApplicationException, TimeoutException, InterruptedException {
			// TODO Auto-generated method stub
			return 0;
		}

		@Override
		public String getRepresentation(Transaction transaction)
				throws RejectedTransactionException, ApplicationException, TimeoutException, InterruptedException {
			// TODO Auto-generated method stub
			return null;
		}

		@Override
		public byte[] getInitialStateId() throws ApplicationException, TimeoutException, InterruptedException {
			// TODO Auto-generated method stub
			return null;
		}

		@Override
		public int beginBlock(long height, LocalDateTime when, byte[] stateId)
				throws UnknownStateException, ApplicationException, TimeoutException, InterruptedException {
			// TODO Auto-generated method stub
			return 0;
		}

		@Override
		public void deliverTransaction(int groupId, Transaction transaction) throws RejectedTransactionException,
				UnknownGroupIdException, ApplicationException, TimeoutException, InterruptedException {
			// TODO Auto-generated method stub
			
		}

		@Override
		public byte[] endBlock(int groupId, Deadline deadline)
				throws ApplicationException, UnknownGroupIdException, TimeoutException, InterruptedException {
			// TODO Auto-generated method stub
			return null;
		}

		@Override
		public void commitBlock(int groupId)
				throws ApplicationException, UnknownGroupIdException, TimeoutException, InterruptedException {
			// TODO Auto-generated method stub
			
		}

		@Override
		public void abortBlock(int groupId)
				throws ApplicationException, UnknownGroupIdException, TimeoutException, InterruptedException {
			// TODO Auto-generated method stub
			
		}

		@Override
		public void close() throws ApplicationException, InterruptedException {
			// TODO Auto-generated method stub
			
		}
	}

	@Name("Special application")
	public static class App42 implements Application {

		@Override
		public void addOnCloseHandler(OnCloseHandler handler) {
			// TODO Auto-generated method stub
			
		}

		@Override
		public void removeOnCloseHandler(OnCloseHandler handler) {
			// TODO Auto-generated method stub
			
		}

		@Override
		public boolean checkPrologExtra(byte[] extra)
				throws ApplicationException, TimeoutException, InterruptedException {
			// TODO Auto-generated method stub
			return false;
		}

		@Override
		public void checkTransaction(Transaction transaction)
				throws RejectedTransactionException, ApplicationException, TimeoutException, InterruptedException {
			// TODO Auto-generated method stub
			
		}

		@Override
		public long getPriority(Transaction transaction)
				throws RejectedTransactionException, ApplicationException, TimeoutException, InterruptedException {
			// TODO Auto-generated method stub
			return 0;
		}

		@Override
		public String getRepresentation(Transaction transaction)
				throws RejectedTransactionException, ApplicationException, TimeoutException, InterruptedException {
			// TODO Auto-generated method stub
			return null;
		}

		@Override
		public byte[] getInitialStateId() throws ApplicationException, TimeoutException, InterruptedException {
			// TODO Auto-generated method stub
			return null;
		}

		@Override
		public int beginBlock(long height, LocalDateTime when, byte[] stateId)
				throws UnknownStateException, ApplicationException, TimeoutException, InterruptedException {
			// TODO Auto-generated method stub
			return 0;
		}

		@Override
		public void deliverTransaction(int groupId, Transaction transaction) throws RejectedTransactionException,
				UnknownGroupIdException, ApplicationException, TimeoutException, InterruptedException {
			// TODO Auto-generated method stub
			
		}

		@Override
		public byte[] endBlock(int groupId, Deadline deadline)
				throws ApplicationException, UnknownGroupIdException, TimeoutException, InterruptedException {
			// TODO Auto-generated method stub
			return null;
		}

		@Override
		public void commitBlock(int groupId)
				throws ApplicationException, UnknownGroupIdException, TimeoutException, InterruptedException {
			// TODO Auto-generated method stub
			
		}

		@Override
		public void abortBlock(int groupId)
				throws ApplicationException, UnknownGroupIdException, TimeoutException, InterruptedException {
			// TODO Auto-generated method stub
			
		}

		@Override
		public void close() throws ApplicationException, InterruptedException {
			// TODO Auto-generated method stub
			
		}
		
	}
}