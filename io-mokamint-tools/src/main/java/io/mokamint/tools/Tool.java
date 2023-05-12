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

package io.mokamint.tools;

import java.io.IOException;
import java.net.URL;
import java.util.function.Supplier;
import java.util.logging.LogManager;

import io.mokamint.tools.internal.POMVersionProvider;
import io.mokamint.tools.internal.PrintExceptionMessageHandler;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.HelpCommand;
import picocli.CommandLine.Option;

/**
 * A command-line tool of Mokamint. Subclasses specify arguments, options and execution.
 */

@Command(name = "mokamint-node",
	subcommands = {
		HelpCommand.class
	},
	versionProvider = POMVersionProvider.class,
	showDefaultValues = true
)
public abstract class Tool {

	@Option(names = { "--version" }, versionHelp = true, description = "print version information and exit")
	private boolean versionRequested;

	protected static void main(Supplier<Tool> tool, String[] args) {
		System.exit(tool.get().run(args));
	}

	public int run(String[] args) {
		return new CommandLine(this)
			.setExecutionExceptionHandler(new PrintExceptionMessageHandler())
			.execute(args);
	}

	static {
		String current = System.getProperty("java.util.logging.config.file");
		if (current == null) {
			// if the property is not set, we provide a default (if it exists)
			URL resource = Tool.class.getClassLoader().getResource("logging.properties");
			if (resource != null)
				try (var is = resource.openStream()) {
					LogManager.getLogManager().readConfiguration(is);
				}
				catch (SecurityException | IOException e) {
					throw new RuntimeException("Cannot load the logging.properties file", e);
				}
		}
	}
}