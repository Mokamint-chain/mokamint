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

package io.mokamint.node.tools.internal.tasks;

import static io.hotmoka.exceptions.CheckSupplier.check;
import static io.hotmoka.exceptions.UncheckFunction.uncheck;

import java.util.concurrent.TimeoutException;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import io.mokamint.node.TaskInfos;
import io.mokamint.node.api.ClosedNodeException;
import io.mokamint.node.api.TaskInfo;
import io.mokamint.node.remote.api.RemotePublicNode;
import io.mokamint.node.tools.internal.AbstractPublicRpcCommand;
import io.mokamint.tools.CommandException;
import jakarta.websocket.EncodeException;
import picocli.CommandLine.Command;

@Command(name = "ls", description = "List the tasks of a node.")
public class List extends AbstractPublicRpcCommand {

	private void body(RemotePublicNode remote) throws TimeoutException, InterruptedException, ClosedNodeException, CommandException {
		try {
			TaskInfo[] infos = remote.getTaskInfos().sorted().toArray(TaskInfo[]::new);

			if (json()) {
				var encoder = new TaskInfos.Encoder();
				System.out.println(check(EncodeException.class, () ->
					Stream.of(infos).map(uncheck(encoder::encode)).collect(Collectors.joining(",", "[", "]"))
				));
			}
			else if (infos.length > 0)
				Stream.of(infos).map(info -> formatLine(info)).forEachOrdered(System.out::println);
		}
		catch (EncodeException e) {
			throw new CommandException("Cannot encode the tasks of the node at " + publicUri() + " in JSON format!", e);
		}
	}

	private String formatLine(TaskInfo info) {
		return formatLine(info.getDescription());
	}

	private String formatLine(String description) {
		return String.format("%-80s", description);
	}

	@Override
	protected void execute() throws CommandException {
		execute(this::body);
	}
}