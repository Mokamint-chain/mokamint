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

package io.mokamint.node.tools.internal.chain;

import java.util.concurrent.TimeoutException;
import java.util.logging.Level;
import java.util.logging.Logger;

import io.mokamint.node.ChainInfos;
import io.mokamint.node.api.ClosedNodeException;
import io.mokamint.node.api.DatabaseException;
import io.mokamint.node.remote.RemotePublicNode;
import io.mokamint.node.tools.internal.AbstractPublicRpcCommand;
import jakarta.websocket.EncodeException;
import picocli.CommandLine.Command;
import picocli.CommandLine.Help.Ansi;

@Command(name = "info", description = "Show information about the chain of a node.")
public class Info extends AbstractPublicRpcCommand {

	private final static Logger LOGGER = Logger.getLogger(Info.class.getName());

	private void body(RemotePublicNode remote) throws TimeoutException, InterruptedException, ClosedNodeException {
		try {
			var info = remote.getChainInfo();

			if (json())
				System.out.println(new ChainInfos.Encoder().encode(info));
			else
				System.out.println(info);
		}
		catch (EncodeException e) {
			System.out.println(Ansi.AUTO.string("@|red Cannot encode in JSON format!|@"));
			LOGGER.log(Level.SEVERE, "cannot encode the chain info of the node at \"" + publicUri() + "\" in JSON format.", e);
		}
		catch (DatabaseException e) {
			System.out.println(Ansi.AUTO.string("@|red The database of the node at \"" + publicUri() + "\" seems corrupted!|@"));
			LOGGER.log(Level.SEVERE, "error accessing the database of the node at \"" + publicUri() + "\".", e);
		}
	}

	@Override
	protected void execute() {
		execute(this::body, LOGGER);
	}
}