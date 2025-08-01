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

package io.mokamint.miner.cli;

import java.io.IOException;

import io.hotmoka.cli.AbstractPropertyFileVersionProvider;
import io.hotmoka.cli.AbstractCLI;
import io.mokamint.miner.cli.MokamintMiner.POMVersionProvider;
import io.mokamint.miner.cli.internal.Info;
import io.mokamint.miner.cli.internal.Start;
import picocli.CommandLine.Command;

/**
 * A command-line interface for working with Mokamint miners.
 * 
 * This class is meant to be run from the parent directory, after building the project, with this command-line:
 * 
 * java --module-path modules/explicit:modules/automatic --class-path "modules/unnamed/*" --module io.mokamint.miner.cli/io.mokamint.miner.cli.MokamintMiner
 */
@Command(
	name = "mokamint-miner",
	header = "This is the command-line tool for Mokamint miners.",
	footer = "Copyright (c) 2023 Fausto Spoto (fausto.spoto@mokamint.io)",
	versionProvider = POMVersionProvider.class,
	subcommands = {
		Info.class,
		Start.class
	}
)
public class MokamintMiner extends AbstractCLI {

	private MokamintMiner() {}

	/**
	 * Entry point from the shell.
	 * 
	 * @param args the command-line arguments provided to this tool
	 */
	public static void main(String[] args) {
		main(MokamintMiner::new, args);
	}

	static {
		loadLoggingConfig(() -> MokamintMiner.class.getModule().getResourceAsStream("logging.properties"));
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
			return getVersion(() -> MokamintMiner.class.getModule().getResourceAsStream("maven.properties"), "mokamint.version");
		}
	}
}