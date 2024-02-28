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
import java.util.ServiceLoader.Provider;
import java.util.concurrent.TimeoutException;

import io.hotmoka.closeables.api.OnCloseHandler;
import io.mokamint.application.Applications;
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
import io.mokamint.tools.AbstractRow;
import io.mokamint.tools.AbstractTable;
import io.mokamint.tools.CommandException;
import io.mokamint.tools.Table;
import picocli.CommandLine.Command;
import picocli.CommandLine.Help.Ansi;
import picocli.CommandLine.Option;

@Command(name = "ls",
	description = "List the available applications.",
	showDefaultValues = true)
public class List extends AbstractCommand {

	@Option(names = "--json", description = "print the output in JSON", defaultValue = "false")
	private boolean json;

	@Override
	protected void execute() throws CommandException {
		new MyTable().print();
	}

	private static class Row extends AbstractRow {
		private final String name;
		private final String className;
		private final String description;
	
		private Row(String name, String className, String description) {
			this.name = name;
			this.className = className;
			this.description = description;
		}
	
		@Override
		public String getColumn(int index) {
			switch (index) {
			case 0: return name;
			case 1: return className;
			case 2: return description;
			default: throw new IndexOutOfBoundsException(index);
			}
		}
	
		@Override
		public String toString(int pos, Table table) {
			String result;
	
			if (pos == 0)
				result = String.format("%s  %s   %s",
						center(name, table.getSlotsForColumn(0)),
						center(className, table.getSlotsForColumn(1)),
						center(description, table.getSlotsForColumn(2)));
			else
				result = String.format("%s  %s   %s",
						leftAlign(name, table.getSlotsForColumn(0)),
						leftAlign(className, table.getSlotsForColumn(1)),
						leftAlign(description, table.getSlotsForColumn(2)));
	
			return pos == 0 ? Ansi.AUTO.string("@|green " + result + "|@") : result;
		}
	}

	private class MyTable extends AbstractTable {
		private MyTable() {
			super(new Row("name", "class", "description"), 3, json);
			Applications.available().forEach(this::add);
		}

		private void add(Provider<Application> provider) {
			var type = provider.type();
			Name annName = type.getAnnotation(Name.class);
			String name = annName != null ? annName.value() : "---";
			Description annDescription = type.getAnnotation(Description.class);
			String description = annDescription != null ? annDescription.value() : "---";
			add(new Row(name, type.getName(), description));
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