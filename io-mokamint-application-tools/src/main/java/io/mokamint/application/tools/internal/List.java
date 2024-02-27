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
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

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
import picocli.CommandLine.Help.Ansi;

@Command(name = "ls",
	description = "List the available applications.",
	showDefaultValues = true)
public class List extends AbstractCommand {

	@Override
	protected void execute() throws CommandException {
		new ListApplications();
	}

	private class ListApplications {
		private final java.util.List<Provider<Application>> providers;
		private final String[] names;
		private final int slotsForNames;
		private final String[] classNames;
		private final int slotsForClassNames;
		private final String[] descriptions;
		private final int slotsForDescriptions;
		/**
		 * Lists the available applications.
		 */
		private ListApplications() {
			ServiceLoader<Application> serviceLoader = ServiceLoader.load(Application.class);
			this.providers = serviceLoader.stream().collect(Collectors.toList());
			this.names = new String[1 + providers.size()];
			this.classNames = new String[names.length];
			this.descriptions = new String[names.length];
			fillColumns();
			this.slotsForNames = Stream.of(names).mapToInt(String::length).max().getAsInt();
			this.slotsForClassNames = Stream.of(classNames).mapToInt(String::length).max().getAsInt();
			this.slotsForDescriptions = Stream.of(descriptions).mapToInt(String::length).max().getAsInt();
			printRows();
		}

		private void fillColumns() {
			names[0] = "name";
			classNames[0] = "class";
			descriptions[0] = "description";
			
			for (int pos = 1; pos < names.length; pos++) {
				var type = providers.get(pos - 1).type();
				classNames[pos] = type.getName();
				Name annName = type.getAnnotation(Name.class);
				names[pos] = annName != null ? annName.value() : "---";
				Description annDescription = type.getAnnotation(Description.class);
				descriptions[pos] = annDescription != null ? annDescription.value() : "---";
			}
		}

		private void printRows() {
			IntStream.iterate(0, i -> i + 1).limit(names.length).mapToObj(this::format).forEach(System.out::println);
		}

		private String format(int pos) {
			String result;

			if (pos == 0)
				result = String.format("%s  %s   %s",
						center(names[pos], slotsForNames),
						center(classNames[pos], slotsForClassNames),
						center(descriptions[pos], slotsForDescriptions));
			else
				result = String.format("%s  %s   %s",
						leftAlign(names[pos], slotsForNames),
						leftAlign(classNames[pos], slotsForClassNames),
						leftAlign(descriptions[pos], slotsForDescriptions));

			return pos == 0 ? Ansi.AUTO.string("@|green " + result + "|@") : result;
		}
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