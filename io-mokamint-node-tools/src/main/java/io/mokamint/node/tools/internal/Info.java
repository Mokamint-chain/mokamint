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

package io.mokamint.node.tools.internal;

import java.util.concurrent.TimeoutException;

import io.mokamint.node.NodeInfos;
import io.mokamint.node.api.ClosedNodeException;
import io.mokamint.node.remote.api.RemotePublicNode;
import io.mokamint.tools.CommandException;
import jakarta.websocket.EncodeException;
import picocli.CommandLine.Command;

@Command(name = "info", description = "Show information about a node.")
public class Info extends AbstractPublicRpcCommand {

	private void body(RemotePublicNode remote) throws TimeoutException, InterruptedException, ClosedNodeException, CommandException {
		try {
			var info = remote.getInfo();

			if (json())
				System.out.println(new NodeInfos.Encoder().encode(info));
			else
				System.out.println(info);
		}
		catch (EncodeException e) {
			throw new CommandException("Cannot encode the info in JSON format!", e);
		}
	}

	@Override
	protected void execute() throws CommandException {
		execute(this::body);
	}
}