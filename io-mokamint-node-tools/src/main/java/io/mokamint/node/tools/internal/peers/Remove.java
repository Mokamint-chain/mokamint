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

package io.mokamint.node.tools.internal.peers;

import java.net.URI;
import java.util.logging.Logger;

import io.mokamint.tools.AbstractCommand;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

@Command(name = "rm", description = "Remove peers from a node.")
public class Remove extends AbstractCommand {

	@Option(names = "--uri", description = "the network URI where the node is published", defaultValue = "ws://localhost:8025")
	private URI uri;

	@Option(names = "--timeout", description = "the timeout of the connection, in milliseconds", defaultValue = "10000")
	private long timeout;

	private final static Logger LOGGER = Logger.getLogger(Remove.class.getName());

	@Override
	protected void execute() {
	}
}