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

import io.mokamint.miner.tools.internal.Start;
import io.mokamint.tools.Tool;
import picocli.CommandLine.Command;

/**
 * A command-line interface for starting Mokamint miners.
 * 
 * This class is meant to be run from the parent directory, after building the project, with this command-line:
 * 
 * java --module-path modules/explicit:modules/automatic --class-path "modules/unnamed/*" --module io.mokamint.miner.tools/io.mokamint.miner.tools.MokamintMiner
 */
@Command(name = "mokamint-miner",
	subcommands = {
		Start.class
	},
	header = "This is the command-line tool for Mokamint miners."
)
public class MokamintMiner extends Tool {

	public static void main(String[] args) {
		main(MokamintMiner::new, args);
	}
}