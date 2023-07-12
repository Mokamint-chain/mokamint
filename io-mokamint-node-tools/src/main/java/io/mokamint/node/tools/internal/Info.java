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
import java.util.logging.Level;
import java.util.logging.Logger;

import io.mokamint.node.NodeInfos;
import io.mokamint.node.remote.RemotePublicNode;
import jakarta.websocket.EncodeException;
import picocli.CommandLine.Command;
import picocli.CommandLine.Help.Ansi;

@Command(name = "info", description = "Show information about a node.")
public class Info extends AbstractPublicRpcCommand {

	private final static Logger LOGGER = Logger.getLogger(Info.class.getName());

	private void body(RemotePublicNode remote) throws TimeoutException, InterruptedException {
		try {
			var info = remote.getInfo();

			if (json())
				System.out.println(new NodeInfos.Encoder().encode(info));
			else
				System.out.println(info);
		}
		catch (EncodeException e) {
			System.out.println(Ansi.AUTO.string("@|red Cannot encode in JSON format!|@"));
			LOGGER.log(Level.SEVERE, "cannot encode the node info of the node at \"" + publicUri() + "\" in JSON format.", e);
		}
	}

	@Override
	protected void execute() {
		execute(this::body, LOGGER);
	}
}