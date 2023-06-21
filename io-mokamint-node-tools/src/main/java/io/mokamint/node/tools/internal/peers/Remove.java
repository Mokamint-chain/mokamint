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

package io.mokamint.node.tools.internal.peers;

import java.net.URI;
import java.util.concurrent.TimeoutException;
import java.util.logging.Logger;
import java.util.stream.Stream;

import io.mokamint.node.Peers;
import io.mokamint.node.remote.RemoteRestrictedNode;
import io.mokamint.node.tools.internal.AbstractRestrictedRpcCommand;
import picocli.CommandLine.Command;
import picocli.CommandLine.Parameters;

@Command(name = "rm", description = "Remove peers from a node.")
public class Remove extends AbstractRestrictedRpcCommand {

	@Parameters(description = { "the URIs of the peers to remove" })
	private URI[] uris;

	private final static Logger LOGGER = Logger.getLogger(Remove.class.getName());

	private void body(RemoteRestrictedNode remote) throws TimeoutException, InterruptedException {
		remote.removePeers(Stream.of(uris).map(Peers::of));
	}

	@Override
	protected void execute() {
		if (uris == null)
			uris = new URI[0];

		execute(this::body, LOGGER);
	}
}