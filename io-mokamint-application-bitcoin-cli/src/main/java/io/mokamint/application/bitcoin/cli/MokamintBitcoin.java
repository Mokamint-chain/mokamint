/*
Copyright 2026 Fausto Spoto

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

package io.mokamint.application.bitcoin.cli;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;

import io.hotmoka.cli.AbstractCLI;
import io.hotmoka.cli.AbstractPropertyFileVersionProvider;
import io.mokamint.application.bitcoin.cli.MokamintBitcoin.POMVersionProvider;
import io.mokamint.application.bitcoin.cli.internal.Requests;
import io.mokamint.constants.Constants;
import picocli.CommandLine.Command;

/**
 * A command-line interface for controlling the Bitcoin Mokamint application.
 * 
 * This class is meant to be run from the parent directory, after building the project, with this command-line:
 * 
 * java --module-path modules/explicit:modules/automatic --class-path "modules/unnamed/*" --module io.mokamint.application.bitcoin.cli/io.mokamint.application.bitcoin.cli.MokamintBitcoin
 */
@Command(
	name = "mokamint-node",
	header = "This is the command-line tool for controlling the Bitcoin Mokamint application.",
	footer = "Copyright (c) 2026 Fausto Spoto (fausto.spoto@mokamint.io)",
	versionProvider = POMVersionProvider.class,
	subcommands = {
		Requests.class
	}
)
public class MokamintBitcoin extends AbstractCLI {

	private MokamintBitcoin() {}

	/**
	 * Entry point from the shell.
	 * 
	 * @param args the command-line arguments provided to this tool
	 */
	public static void main(String[] args) {
		main(MokamintBitcoin::new, args);
	}

	/**
	 * Runs the {@code requests send} command with the given arguments.
	 * 
	 * @param args the arguments
	 * @return what the tool has written into the standard output
	 * @throws IOException if the construction of the return value failed
	 */
	public static String requestsSend(String args) throws IOException {
		return run("requests send " + args);
	}

	/**
	 * Runs the given command-line with the crypto tool, inside a sand-box where the
	 * standard output is redirected into the resulting string. It performs as calling "mokamint-node command".
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
			main(MokamintBitcoin::new, command.split(" "));
			return new String(baos.toByteArray());
		}
		finally {
			System.setOut(originalOut);
			System.setErr(originalErr);
		}
	}

	static {
		loadLoggingConfig(() -> MokamintBitcoin.class.getModule().getResourceAsStream("logging.properties"));
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
		public String[] getVersion() {
			return new String[] { Constants.MOKAMINT_VERSION };
		}
	}
}