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

package io.mokamint.node.cli.internal.miners;

import java.util.UUID;
import java.util.concurrent.TimeoutException;

import io.hotmoka.cli.CommandException;
import io.mokamint.node.api.DatabaseException;
import io.mokamint.node.api.NodeException;
import io.mokamint.node.cli.internal.AbstractRestrictedRpcCommand;
import io.mokamint.node.remote.api.RemoteRestrictedNode;
import picocli.CommandLine.Command;
import picocli.CommandLine.Parameters;

@Command(name = "rm", description = "Remove a miner from a node.")
public class Remove extends AbstractRestrictedRpcCommand {

	@Parameters(description = "the UUID of the miner to remove")
	private UUID uuid;

	private void body(RemoteRestrictedNode remote) throws NodeException, TimeoutException, InterruptedException, CommandException, DatabaseException {
		if (remote.removeMiner(uuid))
			if (json())
				System.out.println(uuid);
			else
				System.out.println("Closed miner " + uuid);
		else
			throw new CommandException("Miner " + uuid + " has not been removed from the set of miners: are you sure that it exists?");
	}

	@Override
	protected void execute() throws CommandException {
		execute(this::body);
	}
}