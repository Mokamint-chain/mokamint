/*
Copyright 2025 Fausto Spoto

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

package io.mokamint.miner.cli.internal;

import java.net.URI;
import java.util.concurrent.TimeoutException;

import io.hotmoka.cli.AbstractRpcCommand;
import io.hotmoka.cli.CommandException;
import io.mokamint.miner.MiningSpecifications;
import io.mokamint.miner.api.ClosedMinerException;
import io.mokamint.miner.api.MiningSpecification;
import io.mokamint.miner.service.MinerServices;
import io.mokamint.miner.service.api.MinerService;
import jakarta.websocket.EncodeException;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

@Command(name = "info",
	description = "Show the specification of a remote miner.",
	showDefaultValues = true)
public class Info extends AbstractRpcCommand<MinerService> {

	@Option(names = "--uri", description = "the network URI where the API of the remote miner is published", defaultValue = "ws://localhost:8025")
	private URI uri;

	protected Info() {
	}

	@Override
	protected void execute() throws CommandException {
		execute(MinerServices::of, this::body, uri);
	}

	private void body(MinerService service) throws TimeoutException, InterruptedException, CommandException {
		try {
			MiningSpecification miningSpecification = service.getMiningSpecification();

			if (json()) {
				try {
					System.out.println(new MiningSpecifications.Encoder().encode(miningSpecification));
				}
				catch (EncodeException e) {
					throw new CommandException("Could not encode the mining specification in JSON format: " + e.getMessage());
				}
			}
			else
				System.out.println(miningSpecification);
		}
		catch (ClosedMinerException e) {
			throw new CommandException("The mining remote has been closed: " + e.getMessage());
		}
	}
}