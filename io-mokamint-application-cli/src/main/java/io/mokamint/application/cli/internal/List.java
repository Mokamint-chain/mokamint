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

package io.mokamint.application.cli.internal;

import java.util.ServiceLoader.Provider;

import io.hotmoka.cli.AbstractCommand;
import io.hotmoka.cli.AbstractRow;
import io.hotmoka.cli.AbstractTable;
import io.hotmoka.cli.CommandException;
import io.hotmoka.cli.Table;
import io.mokamint.application.Applications;
import io.mokamint.application.api.Application;
import io.mokamint.application.api.Description;
import io.mokamint.application.api.Name;
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
		public int numberOfColumns() {
			return 3;
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
			super(new Row("name", "class", "description"), json);
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
}