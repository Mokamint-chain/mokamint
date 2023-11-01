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

package io.mokamint.node.tools.internal.miners;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeoutException;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import io.mokamint.node.api.ClosedNodeException;
import io.mokamint.node.remote.api.RemoteRestrictedNode;
import io.mokamint.node.tools.internal.AbstractRestrictedRpcCommand;
import io.mokamint.tools.CommandException;
import picocli.CommandLine.Command;
import picocli.CommandLine.Parameters;

@Command(name = "add", description = "Add remote miners to a node.")
public class Add extends AbstractRestrictedRpcCommand {

	@Parameters(description = "the ports where the miners must be published")
	private int[] ports;

	private class Run {
		private final RemoteRestrictedNode remote;
		private final List<String> successes = new ArrayList<>();

		private Run(RemoteRestrictedNode remote) throws ClosedNodeException, TimeoutException, InterruptedException, CommandException {
			this.remote = remote;

			Optional<Exception> exception = IntStream.of(ports)
				.parallel()
				.mapToObj(this::addMiner)
				.flatMap(Optional::stream)
				.findFirst();

			if (json())
				System.out.println(successes.stream().collect(Collectors.joining(", ", "[", "]")));

			if (exception.isPresent())
				throwAsRpcCommandException(exception.get());
		}

		private Optional<Exception> addMiner(int port) {
			try {
				if (remote.openMiner(port)) {
					successes.add(String.valueOf(port));
					if (!json())
						System.out.println("Opened a remote miner at port " + port);
				}
				else if (!json())
					System.out.println("No remote miner has been opened at port " + port);

				return Optional.empty();
			}
			catch (RuntimeException | ClosedNodeException | TimeoutException | InterruptedException e) {
				return Optional.of(e);
			}
			catch (IOException e) {
				return Optional.of(new CommandException("Cannot open a remote miner at port " + port + "!", e));
			}
		}
	}

	@Override
	protected void execute() throws CommandException {
		if (ports == null)
			ports = new int[0];

		execute(Run::new);
	}
}