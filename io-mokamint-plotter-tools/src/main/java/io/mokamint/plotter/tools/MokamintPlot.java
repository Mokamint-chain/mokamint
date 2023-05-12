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

import io.mokamint.plotter.tools.internal.Create;
import io.mokamint.tools.Tool;
import io.mokamint.tools.Version;
import picocli.CommandLine.Command;
import picocli.CommandLine.HelpCommand;

/**
 * A command-line interface for creating Mokamint plot files.
 * 
 * This class is meant to be run from the parent directory, after building the project, with this command-line:
 * 
 * java --module-path modules/explicit:modules/automatic --class-path "modules/unnamed/*" --module io.mokamint.plotter.tools/io.mokamint.plotter.tools.MokamintPlot
 */
@Command(name = "mokamint-plot",
	subcommands = {
		Create.class,
		HelpCommand.class,
		Version.class
	},
	description = "This is the command-line tool for creating Mokamint plots.",
	showDefaultValues = true
)
public class MokamintPlot extends Tool {

	public static void main(String[] args) {
		main(MokamintPlot::new, args);
	}
}