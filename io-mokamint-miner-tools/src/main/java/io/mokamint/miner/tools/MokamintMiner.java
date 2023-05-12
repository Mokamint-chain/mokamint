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

package io.mokamint.miner.tools;

import java.io.IOException;
import java.net.URL;
import java.util.logging.LogManager;

import io.mokamint.miner.tools.internal.PrintExceptionMessageHandler;
import io.mokamint.miner.tools.internal.Start;
import io.mokamint.miner.tools.internal.Version;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.HelpCommand;

/**
 * A command-line interface for creating Mokamint plot files.
 * 
 * This class is meant to be run from the parent directory, after building the project, with this command-line:
 * 
 * java --module-path modules/explicit:modules/automatic --class-path "modules/unnamed/*" --module io.mokamint.plotter.tools/io.mokamint.plotter.tools.MokamintPlot
 */
@Command(name = "mokamint-miner",
	subcommands = {
		HelpCommand.class,
		Start.class,
		Version.class
	},
	description = "This is the command-line tool for Mokamint miners.",
	showDefaultValues = true
)
public class MokamintMiner {

	public static void main(String[] args) throws IOException {
		int exit = new CommandLine(new MokamintMiner())
			.setExecutionExceptionHandler(new PrintExceptionMessageHandler())
			.execute(args);

		System.exit(exit);
	}

	public static void run(String command) {
		new CommandLine(new MokamintMiner())
			.setExecutionExceptionHandler(new PrintExceptionMessageHandler())
			.execute(command.split(" "));
	}

	static {
		String current = System.getProperty("java.util.logging.config.file");
		if (current == null) {
			// if the property is not set, we provide a default (if it exists)
			URL resource = MokamintMiner.class.getClassLoader().getResource("logging.properties");
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