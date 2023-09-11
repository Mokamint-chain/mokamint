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
import java.util.stream.Stream;

import io.mokamint.node.MinerInfos;
import io.mokamint.node.api.ClosedNodeException;
import io.mokamint.node.api.MinerInfo;
import io.mokamint.node.remote.RemotePublicNode;
import io.mokamint.node.tools.internal.AbstractPublicRpcCommand;
import io.mokamint.tools.CommandException;
import jakarta.websocket.EncodeException;
import picocli.CommandLine.Command;
import picocli.CommandLine.Help.Ansi;

@Command(name = "ls", description = "List the miners of a node.")
public class List extends AbstractPublicRpcCommand {

	private void body(RemotePublicNode remote) throws TimeoutException, InterruptedException, ClosedNodeException {
		try {
			MinerInfo[] infos = remote.getMinerInfos().sorted().toArray(MinerInfo[]::new);
			if (infos.length == 0)
				return;

			if (json()) {
				var encoder = new MinerInfos.Encoder();
				System.out.println(check(EncodeException.class, () ->
					Stream.of(infos).map(uncheck(encoder::encode)).collect(Collectors.joining(",", "[", "]"))
				));
			}
			else {
				System.out.println(Ansi.AUTO.string("@|green " + formatLine("UUID", "points", "description") + "|@"));
				Stream.of(infos).map(info -> formatLine(info)).forEachOrdered(System.out::println);
			}
		}
		catch (EncodeException e) {
			throw new CommandException("Cannot encode the miners of the node at " + publicUri() + " in JSON format!", e);
		}
	}

	private String formatLine(MinerInfo info) {
		return formatLine(info.getUUID().toString(), String.valueOf(info.getPoints()), info.getDescription());
	}

	private String formatLine(String uuid, String points, String description) {
		return String.format("%-36s   %6s  %-36s", uuid, points, description);
	}

	@Override
	protected void execute() {
		execute(this::body);
	}
}