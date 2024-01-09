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
import java.util.UUID;
import java.util.concurrent.TimeoutException;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import io.mokamint.node.api.ClosedNodeException;
import io.mokamint.node.api.DatabaseException;
import io.mokamint.node.remote.api.RemoteRestrictedNode;
import io.mokamint.node.tools.internal.AbstractRestrictedRpcCommand;
import io.mokamint.tools.CommandException;
import picocli.CommandLine.Command;
import picocli.CommandLine.Parameters;

@Command(name = "rm", description = "Remove miners from a node.")
public class Remove extends AbstractRestrictedRpcCommand {

	@Parameters(description = "the UUIDs of the miners to remove")
	private UUID[] uuids;

	private class Run {
		private final RemoteRestrictedNode remote;
		private final List<String> successes = new ArrayList<>();

		private Run(RemoteRestrictedNode remote) throws ClosedNodeException, TimeoutException, InterruptedException, CommandException, DatabaseException {
			if (uuids == null || uuids.length == 0)
				throw new CommandException("No miners have been specified!");

			this.remote = remote;

			Optional<Exception> exception = Stream.of(uuids)
				.parallel()
				.map(this::closeMiner)
				.flatMap(Optional::stream)
				.findFirst();

			if (json())
				System.out.println(successes.stream().collect(Collectors.joining(", ", "[", "]")));
			else
				successes.stream().forEach(uuid -> System.out.println("Removed " + uuid + " from the set of miners"));

			if (exception.isPresent())
				throwAsRpcCommandException(exception.get());
		}

		private Optional<Exception> closeMiner(UUID uuid) {
			try {
				if (remote.removeMiner(uuid)) {
					successes.add(uuid.toString());
					return Optional.empty();
				}
				else
					return Optional.of(new CommandException("Miner " + uuid + " has not been removed from the set of peers: are you sure that it exists?"));
			}
			catch (InterruptedException e) {
				Thread.currentThread().interrupt();
				return Optional.of(e);
			}
			catch (RuntimeException | ClosedNodeException | TimeoutException e) {
				return Optional.of(e);
			}
			catch (IOException e) {
				return Optional.of(new CommandException("Cannot close miner " + uuid, e));
			}
		}
	}

	@Override
	protected void execute() throws CommandException {
		execute(Run::new);
	}
}