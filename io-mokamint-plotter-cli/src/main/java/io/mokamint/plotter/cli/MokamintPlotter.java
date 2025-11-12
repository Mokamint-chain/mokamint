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

package io.mokamint.plotter.cli;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;

import io.hotmoka.cli.AbstractCLI;
import io.hotmoka.cli.AbstractPropertyFileVersionProvider;
import io.mokamint.plotter.cli.MokamintPlotter.POMVersionProvider;
import io.mokamint.plotter.cli.internal.Keys;
import picocli.CommandLine.Command;

/**
 * A command-line interface for creating Mokamint plot files.
 * 
 * This class is meant to be run from the parent directory, after building the project, with this command-line:
 * 
 * java --module-path modules/explicit:modules/automatic --class-path "modules/unnamed/*" --module io.mokamint.plotter.cli/io.mokamint.plotter.cli.MokamintPlotter
 */
@Command(
	name = "mokamint-plotter",
	header = "This is the command-line tool for creating Mokamint plots.",
	footer = "Copyright (c) 2023 Fausto Spoto (fausto.spoto@mokamint.io)",
	versionProvider = POMVersionProvider.class,
	subcommands = {
		Create.class,
		Keys.class,
		Show.class
	}
)
public class MokamintPlotter extends AbstractCLI {

	private MokamintPlotter() {}

	/**
	 * Entry point from the shell.
	 * 
	 * @param args the command-line arguments provided to this tool
	 */
	public static void main(String[] args) {
		main(MokamintPlotter::new, args);
	}

	/**
	 * Runs the {@code create} command with the given arguments.
	 * 
	 * @param args the arguments
	 * @return what the tool has written into the standard output
	 * @throws IOException if the construction of the return value failed
	 */
	public static String create(String args) throws IOException {
		return run("create " + args);
	}

	/**
	 * Runs the {@code show} command with the given arguments.
	 * 
	 * @param args the arguments
	 * @return what the tool has written into the standard output
	 * @throws IOException if the construction of the return value failed
	 */
	public static String show(String args) throws IOException {
		return run("show " + args);
	}

	/**
	 * Runs the {@code keys create} command with the given arguments.
	 * 
	 * @param args the arguments
	 * @return what the tool has written into the standard output
	 * @throws IOException if the construction of the return value failed
	 */
	public static String keysCreate(String args) throws IOException {
		return run("keys create " + args);
	}

	/**
	 * Runs the {@code keys show} command with the given arguments.
	 * 
	 * @param args the arguments
	 * @return what the tool has written into the standard output
	 * @throws IOException if the construction of the return value failed
	 */
	public static String keysShow(String args) throws IOException {
		return run("keys show " + args);
	}

	/**
	 * Runs the {@code keys export} command with the given arguments.
	 * 
	 * @param args the arguments
	 * @return what the tool has written into the standard output
	 * @throws IOException if the construction of the return value failed
	 */
	public static String keysExport(String args) throws IOException {
		return run("keys export " + args);
	}

	/**
	 * Runs the {@code keys import} command with the given arguments.
	 * 
	 * @param args the arguments
	 * @return what the tool has written into the standard output
	 * @throws IOException if the construction of the return value failed
	 */
	public static String keysImport(String args) throws IOException {
		return run("keys import " + args);
	}

	/**
	 * Runs the given command-line with the crypto tool, inside a sand-box where the
	 * standard output is redirected into the resulting string. It performs as calling "crypto command".
	 * 
	 * @param command the command to run with crypto
	 * @return what the moka tool has written into the standard output
	 * @throws IOException if the construction of the return value failed
	 */
	private static String run(String command) throws IOException {
		var originalOut = System.out;
		var originalErr = System.err;
	
		try (var baos = new ByteArrayOutputStream(); var out = new PrintStream(baos)) {
			System.setOut(out);
			System.setErr(out);
			main(MokamintPlotter::new, command.split(" "));
			return new String(baos.toByteArray());
		}
		finally {
			System.setOut(originalOut);
			System.setErr(originalErr);
		}
	}

	static {
		loadLoggingConfig(() -> MokamintPlotter.class.getModule().getResourceAsStream("logging.properties"));
	}

	/**
	 * A provider of the version of this tool, taken from the property
	 * declaration into the POM file.
	 */
	public static class POMVersionProvider extends AbstractPropertyFileVersionProvider {

		/**
		 * Creates the provider.
		 */
		public POMVersionProvider() {}

		@Override
		public String[] getVersion() throws IOException {
			return getVersion(() -> MokamintPlotter.class.getModule().getResourceAsStream("maven.properties"), "mokamint.version");
		}
	}
}