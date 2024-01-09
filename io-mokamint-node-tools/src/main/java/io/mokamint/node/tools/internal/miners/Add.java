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
import java.util.HashSet;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeoutException;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import io.mokamint.node.MinerInfos;
import io.mokamint.node.api.ClosedNodeException;
import io.mokamint.node.api.DatabaseException;
import io.mokamint.node.api.MinerInfo;
import io.mokamint.node.remote.api.RemoteRestrictedNode;
import io.mokamint.node.tools.internal.AbstractRestrictedRpcCommand;
import io.mokamint.tools.CommandException;
import jakarta.websocket.EncodeException;
import picocli.CommandLine.Command;
import picocli.CommandLine.Parameters;

@Command(name = "add", description = "Add remote miners to a node.")
public class Add extends AbstractRestrictedRpcCommand {

	@Parameters(description = "the ports where the miners must be published")
	private int[] ports;

	private class Run {
		private final RemoteRestrictedNode remote;
		private final java.util.List<MinerInfo> successes = new CopyOnWriteArrayList<>();
		private final java.util.List<Exception> exceptions = new CopyOnWriteArrayList<>();

		private Run(RemoteRestrictedNode remote) throws ClosedNodeException, TimeoutException, InterruptedException, CommandException, DatabaseException {
			if (ports == null || ports.length == 0)
				throw new CommandException("No ports have been specified!");

			this.remote = remote;

			IntStream.of(ports)
				.parallel()
				.forEachOrdered(this::addMiner);

			if (json()) {
				var encoder = new MinerInfos.Encoder();
				var opened = new HashSet<String>();
				for (var info: successes)
					opened.add(encode(info, encoder));

				System.out.println(opened.stream().collect(Collectors.joining(",", "[", "]")));
			}
			else
				successes.stream().map(mi -> "Opened " + mi).forEach(System.out::println);

			if (!exceptions.isEmpty())
				throwAsRpcCommandException(exceptions.get(0));
		}

		private String encode(MinerInfo info, MinerInfos.Encoder encoder) throws CommandException {
			try {
				return encoder.encode(info);
			}
			catch (EncodeException e) {
				throw new CommandException("Cannot encode " + info + " in JSON", e);
			}
		}

		private void addMiner(int port) {
			try {
				remote.openMiner(port).ifPresentOrElse(successes::add, () -> {
					if (!json())
						System.out.println("No remote miner has been opened at port " + port);
				});
			}
			catch (InterruptedException e) {
				Thread.currentThread().interrupt();
				exceptions.add(e);
			}
			catch (RuntimeException | ClosedNodeException | TimeoutException e) {
				exceptions.add(e);
			}
			catch (IOException e) {
				exceptions.add(new CommandException("Cannot open a remote miner at port " + port + "!", e));
			}
		}
	}

	@Override
	protected void execute() throws CommandException {
		execute(Run::new);
	}
}