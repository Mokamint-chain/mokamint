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

package io.mokamint.application.cli;

import java.io.IOException;

import io.hotmoka.cli.AbstractPropertyFileVersionProvider;
import io.hotmoka.cli.AbstractCLI;
import io.mokamint.application.cli.MokamintApplication.POMVersionProvider;
import io.mokamint.application.cli.internal.List;
import io.mokamint.application.cli.internal.Start;
import picocli.CommandLine.Command;

/**
 * A command-line interface for working with Mokamint applications.
 * 
 * This class is meant to be run from the parent directory, after building the project, with this command-line:
 * 
 * java --module-path modules/explicit:modules/automatic --class-path "modules/unnamed/*" --module io.mokamint.application.cli/io.mokamint.application.cli.MokamintApplication
 */
@Command(
	name = "mokamint-application",
	header = "This is the command-line tool for Mokamint applications.",
	footer = "Copyright (c) 2024 Fausto Spoto",
	versionProvider = POMVersionProvider.class,
	subcommands = {
		List.class,
		Start.class
	}
)
public class MokamintApplication extends AbstractCLI {

	private MokamintApplication() {}

	/**
	 * Entry point from the shell.
	 * 
	 * @param args the command-line arguments provided to this tool
	 */
	public static void main(String[] args) {
		main(MokamintApplication::new, args);
	}

	static {
		loadLoggingConfig(() -> MokamintApplication.class.getModule().getResourceAsStream("logging.properties"));
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
			return getVersion(() -> MokamintApplication.class.getModule().getResourceAsStream("maven.properties"), "mokamint.version");
		}
	}
}