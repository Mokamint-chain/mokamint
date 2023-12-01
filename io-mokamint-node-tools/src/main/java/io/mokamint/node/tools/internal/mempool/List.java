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

package io.mokamint.node.tools.internal.mempool;

import java.util.concurrent.TimeoutException;
import java.util.logging.Logger;

import io.mokamint.node.MempoolPortions;
import io.mokamint.node.api.ClosedNodeException;
import io.mokamint.node.api.MempoolEntry;
import io.mokamint.node.remote.api.RemotePublicNode;
import io.mokamint.node.tools.internal.AbstractPublicRpcCommand;
import io.mokamint.tools.CommandException;
import jakarta.websocket.EncodeException;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

@Command(name = "ls", description = "List the transactions in the mempool of a node.")
public class List extends AbstractPublicRpcCommand {

	@Parameters(description = "the number of transactions that must be listed", defaultValue = "100")
	private int count;

	@Option(names = "from", description = "the index of the first transaction that must be reported (-1 to list the highest-priority count transactions)", defaultValue = "-1")
	private int from;

	private final static Logger LOGGER = Logger.getLogger(List.class.getName());

	private void body(RemotePublicNode remote) throws TimeoutException, InterruptedException, ClosedNodeException, CommandException {
		if (count < 0)
			throw new CommandException("count cannot be negative!");

		if (from < -1)
			throw new CommandException("from cannot be smaller than -1!");

		if (from == -1L)
			from = (int) Math.max(0, remote.getMempoolInfo().getSize() - count);

		LOGGER.info("requesting mempool entries with index in the interval [" + from + ", " + (from + count) + ")");
		var mempool = remote.getMempoolPortion(from, count);

		if (json()) {
			try {
				System.out.println(new MempoolPortions.Encoder().encode(mempool));
			}
			catch (EncodeException e) {
				throw new CommandException("Cannot encode a mempool portion of the node at \"" + publicUri() + "\" in JSON format!", e);
			}
		}
		else {
			var infos = mempool.getEntries().toArray(MempoolEntry[]::new);
			int height = from + infos.length - 1;
			int slotsForHeight = String.valueOf(height).length();

			for (int counter = infos.length - 1; counter >= 0; counter--, height--)
				System.out.printf("%" + slotsForHeight + "d %s\n", height, infos[counter]);
		}
	}

	@Override
	protected void execute() throws CommandException {
		execute(this::body);
	}
}