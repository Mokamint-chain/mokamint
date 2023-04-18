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

package io.mokamint.node.tools;

import java.io.IOException;
import java.net.URL;
import java.util.logging.LogManager;

import io.mokamint.node.tools.internal.PrintExceptionMessageHandler;
import io.mokamint.node.tools.internal.Start;
import io.mokamint.node.tools.internal.Version;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.HelpCommand;

/**
 * A command-line interface for controlling Mokamint nodes.
 * 
 * This class is meant to be run from the parent directory, after building the project, with this command-line:
 * 
 * java --module-path modules/explicit:modules/automatic --class-path "modules/unnamed/*" --module io.mokamint.node.tools/io.mokamint.node.tools.MokamintNode
 */
@Command(name = "mokamint-node",
	subcommands = {
		HelpCommand.class,
		Start.class,
		Version.class
	},
	description = "This is the command-line tool for controlling Mokamint nodes.",
	showDefaultValues = true
)
public class MokamintNode {

	public static void main(String[] args) throws IOException {
		int exit = new CommandLine(new MokamintNode())
			.setExecutionExceptionHandler(new PrintExceptionMessageHandler())
			.execute(args);

		System.exit(exit);
	}

	public static void run(String command) {
		new CommandLine(new MokamintNode()).setExecutionExceptionHandler(new PrintExceptionMessageHandler()).execute(command.split(" "));
	}

	static {
		String current = System.getProperty("java.util.logging.config.file");
		if (current == null) {
			// if the property is not set, we provide a default (if it exists)
			URL resource = MokamintNode.class.getClassLoader().getResource("logging.properties");
			if (resource != null)
				try (var is = resource.openStream()) {
					LogManager.getLogManager().readConfiguration(is);
				}
				catch (SecurityException | IOException e) {
					throw new RuntimeException("Cannot load logging.properties file", e);
				}
		}
	}
}