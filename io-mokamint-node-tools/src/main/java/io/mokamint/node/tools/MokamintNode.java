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

package io.mokamint.node.tools;

import io.mokamint.cli.AbstractTool;
import io.mokamint.node.tools.internal.Chain;
import io.mokamint.node.tools.internal.Config;
import io.mokamint.node.tools.internal.Info;
import io.mokamint.node.tools.internal.Keys;
import io.mokamint.node.tools.internal.Mempool;
import io.mokamint.node.tools.internal.Miners;
import io.mokamint.node.tools.internal.Peers;
import io.mokamint.node.tools.internal.Start;
import io.mokamint.node.tools.internal.Tasks;
import io.mokamint.node.tools.internal.Transactions;
import picocli.CommandLine.Command;

/**
 * A command-line interface for controlling Mokamint nodes.
 * 
 * This class is meant to be run from the parent directory, after building the project, with this command-line:
 * 
 * java --module-path modules/explicit:modules/automatic --class-path "modules/unnamed/*" --module io.mokamint.node.tools/io.mokamint.node.tools.MokamintNode
 */
@Command(
	name = "mokamint-node",
	header = "This is the command-line tool for controlling Mokamint nodes.",
	footer = "Copyright (c) 2024 Fausto Spoto",
	subcommands = {
		Chain.class,
		Config.class,
		Info.class,
		Keys.class,
		Mempool.class,
		Miners.class,
		Peers.class,
		Start.class,
		Tasks.class,
		Transactions.class
	}
)
public class MokamintNode extends AbstractTool {

	private MokamintNode() {}

	/**
	 * Entry point from the shell.
	 * 
	 * @param args the command-line arguments provided to this tool
	 */
	public static void main(String[] args) {
		main(MokamintNode::new, args);
	}

	static {
		loadLoggingConfig(() -> MokamintNode.class.getModule().getResourceAsStream("logging.properties"));
	}
}