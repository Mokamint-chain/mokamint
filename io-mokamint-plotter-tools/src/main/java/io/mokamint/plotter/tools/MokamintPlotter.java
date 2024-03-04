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

package io.mokamint.plotter.tools;

import io.mokamint.cli.AbstractTool;
import io.mokamint.plotter.tools.internal.Create;
import io.mokamint.plotter.tools.internal.Show;
import picocli.CommandLine.Command;

/**
 * A command-line interface for creating Mokamint plot files.
 * 
 * This class is meant to be run from the parent directory, after building the project, with this command-line:
 * 
 * java --module-path modules/explicit:modules/automatic --class-path "modules/unnamed/*" --module io.mokamint.plotter.tools/io.mokamint.plotter.tools.MokamintPlotter
 */
@Command(
	name = "mokamint-plotter",
	header = "This is the command-line tool for creating Mokamint plots.",
	footer = "Copyright (c) 2023 Fausto Spoto",
	subcommands = {
		Create.class,
		Show.class
	}
)
public class MokamintPlotter extends AbstractTool {

	private MokamintPlotter() {}

	/**
	 * Entry point from the shell.
	 * 
	 * @param args the command-line arguments provided to this tool
	 */
	public static void main(String[] args) {
		main(MokamintPlotter::new, args);
	}

	static {
		loadLoggingConfig(() -> MokamintPlotter.class.getModule().getResourceAsStream("logging.properties"));
	}
}