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

package io.mokamint.node.tools.internal;

import io.mokamint.node.tools.internal.chain.Info;
import io.mokamint.node.tools.internal.chain.List;
import picocli.CommandLine.Command;
import picocli.CommandLine.HelpCommand;

@Command(name = "chain",
	description = "Control the chain of a node.",
	subcommands = {
		HelpCommand.class,
		Info.class,
		List.class
	})
public class Chain {
}