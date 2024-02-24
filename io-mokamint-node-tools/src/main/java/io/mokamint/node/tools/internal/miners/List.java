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

import static io.hotmoka.exceptions.CheckSupplier.check;
import static io.hotmoka.exceptions.UncheckFunction.uncheck;

import java.util.concurrent.TimeoutException;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import io.mokamint.node.MinerInfos;
import io.mokamint.node.api.MinerInfo;
import io.mokamint.node.api.NodeException;
import io.mokamint.node.remote.api.RemotePublicNode;
import io.mokamint.node.tools.internal.AbstractPublicRpcCommand;
import io.mokamint.tools.CommandException;
import jakarta.websocket.EncodeException;
import picocli.CommandLine.Command;
import picocli.CommandLine.Help.Ansi;

@Command(name = "ls", description = "List the miners of a node.")
public class List extends AbstractPublicRpcCommand {

	private void body(RemotePublicNode remote) throws TimeoutException, InterruptedException, NodeException, CommandException {
			var infos = remote.getMinerInfos().sorted().toArray(MinerInfo[]::new);

			if (json()) {
				var encoder = new MinerInfos.Encoder();
				try {
					System.out.println(check(EncodeException.class, () ->
						Stream.of(infos).map(uncheck(encoder::encode)).collect(Collectors.joining(",", "[", "]"))
					));
				}
				catch (EncodeException e) {
					throw new CommandException("Cannot encode the miners of the node at " + publicUri() + " in JSON format!", e);
				}
			}
			else if (infos.length > 0)
				new ListMiners(infos);
	}

	private class ListMiners {
		private final String[] uuids;
		private final int slotsForUUID;
		private final String[] points;
		private final int slotsForPoints;
		private final String[] descriptions;
		private final int slotsForDescription;

		/**
		 * Lists the miners with the given {@code infos}.
		 * 
		 * @param infos the miners information
		 */
		private ListMiners(MinerInfo[] infos) {
			this.uuids = new String[1 + infos.length];
			this.points = new String[uuids.length];
			this.descriptions = new String[uuids.length];
			fillColumns(infos);
			this.slotsForUUID = Stream.of(uuids).mapToInt(String::length).max().getAsInt();
			this.slotsForPoints = Stream.of(points).mapToInt(String::length).max().getAsInt();
			this.slotsForDescription = Stream.of(descriptions).mapToInt(String::length).max().getAsInt();
			printRows();
		}

		private void fillColumns(MinerInfo[] infos) {
			uuids[0] = "UUID";
			points[0] = "points";
			descriptions[0] = "description";
			
			for (int pos = 1; pos < uuids.length; pos++) {
				uuids[pos] = String.valueOf(infos[pos - 1].getUUID());
				points[pos] = String.valueOf(infos[pos - 1].getPoints());
				descriptions[pos] = infos[pos - 1].getDescription();
			}
		}

		private void printRows() {
			IntStream.iterate(0, i -> i + 1).limit(uuids.length).mapToObj(this::format).forEach(System.out::println);
		}

		private String format(int pos) {
			if (pos == 0)
				return Ansi.AUTO.string("@|green " + String.format("%s %s   %s",
						center(uuids[pos], slotsForUUID),
						center(points[pos], slotsForPoints),
						center(descriptions[pos], slotsForDescription)) + "|@");
			else
				return String.format("%s %s   %s",
						center(uuids[pos], slotsForUUID),
						rightAlign(points[pos], slotsForPoints),
						leftAlign(descriptions[pos], slotsForDescription));
		}
	}

	@Override
	protected void execute() throws CommandException {
		execute(this::body);
	}
}