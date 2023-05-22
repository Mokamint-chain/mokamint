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

package io.mokamint.tools.internal;

import java.io.IOException;
import java.net.URL;
import java.util.function.Supplier;
import java.util.logging.LogManager;

import io.mokamint.tools.AbstractTool;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.HelpCommand;
import picocli.CommandLine.Option;

/**
 * Partial implementation of a command-line tool of Mokamint. Subclasses specify arguments, options and execution.
 */
@Command(
	subcommands = {
		HelpCommand.class
	},
	versionProvider = POMVersionProvider.class,
	showDefaultValues = true
)
public abstract class AbstractToolImpl {

	@Option(names = { "--version" }, versionHelp = true, description = "print version information and exit")
	private boolean versionRequested;

	/**
	 * Builds the tool.
	 */
	protected AbstractToolImpl() {}

	/**
	 * Entry point of a tool. This is typically called by the actual {@code main} method
	 * of the tool, providing its same supplier.
	 * 
	 * @param tool the supplier of an object of the tool that will be run
	 * @param args the command-line arguments passed to the tool
	 */
	protected static void main(Supplier<AbstractTool> tool, String[] args) {
		System.exit(tool.get().run(args));
	}

	/**
	 * Runs the tool with the given command-line arguments.
	 * 
	 * @param args the command-line arguments
	 * @return the exit status of the execution
	 */
	public int run(String[] args) {
		return new CommandLine(this)
			.setExecutionExceptionHandler(new PrintExceptionMessageHandler())
			.execute(args);
	}

	static {
		String current = System.getProperty("java.util.logging.config.file");
		if (current == null) {
			// if the property is not set, we provide a default (if it exists)
			URL resource = AbstractToolImpl.class.getClassLoader().getResource("logging.properties");
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