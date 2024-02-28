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

package io.mokamint.application.tools;

import io.mokamint.application.tools.internal.List;
import io.mokamint.application.tools.internal.Start;
import io.mokamint.tools.AbstractTool;
import picocli.CommandLine.Command;

/**
 * A command-line interface for working with Mokamint applications.
 * 
 * This class is meant to be run from the parent directory, after building the project, with this command-line:
 * 
 * java --module-path modules/explicit:modules/automatic --class-path "modules/unnamed/*" --module io.mokamint.application.tools/io.mokamint.application.tools.MokamintApplication
 */
@Command(
	name = "mokamint-application",
	header = "This is the command-line tool for Mokamint applications.",
	footer = "Copyright (c) 2024 Fausto Spoto",
	subcommands = {
		List.class,
		Start.class
	}
)
public class MokamintApplication extends AbstractTool {

	private MokamintApplication() {}

	/**
	 * Entry point from the shell.
	 * 
	 * @param args the command-line arguments provided to this tool
	 */
	public static void main(String[] args) {
		main(MokamintApplication::new, args);
	}
}