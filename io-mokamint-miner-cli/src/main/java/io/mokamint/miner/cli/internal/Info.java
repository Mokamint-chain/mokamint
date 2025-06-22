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

import io.hotmoka.cli.AbstractCommand;
import io.hotmoka.cli.CommandException;
import io.mokamint.miner.MiningSpecifications;
import io.mokamint.miner.api.MinerException;
import io.mokamint.miner.service.MinerServices;
import jakarta.websocket.EncodeException;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

@Command(name = "info",
	description = "Print the specification of a mining remote.",
	showDefaultValues = true)
public class Info extends AbstractCommand {

	@Option(names = "--uri", description = "the URI of the remote mining endpoint", defaultValue = "ws://localhost:8025")
	private URI uri;

	@Override
	protected void execute() throws CommandException {
		new Run();
	}

	private class Run {

		private Run() throws CommandException {
			try (var service = MinerServices.of(uri)) {
				System.out.println(new MiningSpecifications.Encoder().encode(service.getMiningSpecification()));
			}
			catch (MinerException e) {
				throw new CommandException("The miner is misbehaving: " + e.getMessage());
			}
			catch (TimeoutException e) {
				throw new CommandException("The operation has timed out");
			}
			catch (InterruptedException e) {
				Thread.currentThread().interrupt();
				throw new CommandException("The operation has been interrupted");
			}
			catch (EncodeException e) {
				throw new CommandException("Could not encode the mining specification in JSON format: " + e.getMessage());
			}
		}
	}
}