/*
Copyright 2024 Fausto Spoto

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

package io.mokamint.node.cli.internal.requests;

import java.util.concurrent.TimeoutException;

import com.google.gson.Gson;

import io.hotmoka.cli.CommandException;
import io.hotmoka.crypto.Hex;
import io.mokamint.node.api.ClosedNodeException;
import io.mokamint.node.api.Request;
import io.mokamint.node.api.RequestRejectedException;
import io.mokamint.node.cli.AbstractPublicRpcCommand;
import io.mokamint.node.remote.api.RemotePublicNode;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

@Command(name = "show", description = "Show a request in the blockchain of a node.")
public class Show extends AbstractPublicRpcCommand {

	@Parameters(index = "0", description = "the hexadecimal hash of the request")
	private String hash;

	@Option(names = "--representation", description = "report the representation of the request instead of its Base64-encoded bytes", defaultValue = "false")
    private boolean representation;

	private void body(RemotePublicNode remote) throws TimeoutException, InterruptedException, CommandException, ClosedNodeException {
		if (representation) {
			String representation = getRequestRepresentation(remote);

			if (json()) {
				var answer = new RepresentationAnswer();
				answer.representation = representation;
				System.out.println(new Gson().toJsonTree(answer));
			}
			else
				System.out.println(representation);
		}
		else {
			var request = getRequest(remote);

			if (json()) {
				var answer = new RequestAnswer();
				answer.bytes = request.toBase64String();
				System.out.println(new Gson().toJsonTree(answer));
			}
			else
				System.out.println(request);
		}
	}

	private String getRequestRepresentation(RemotePublicNode remote) throws CommandException, TimeoutException, InterruptedException, ClosedNodeException {
		try {
			return remote.getRequestRepresentation(toBytes(hash)).orElseThrow(() -> new CommandException("The blockchain of the node does not contain any request with that hash!"));
		}
		catch (RequestRejectedException e) {
			throw new CommandException("The request exists in blockchain but cannot be transformed into its textual representation", e);
		}
	}

	private Request getRequest(RemotePublicNode remote) throws CommandException, TimeoutException, InterruptedException, ClosedNodeException {
		return remote.getRequest(toBytes(hash)).orElseThrow(() -> new CommandException("The blockchain of the node does not contain any request with that hash!"));
	}

	@Override
	protected void execute() throws CommandException {
		execute(this::body);
	}

	private static byte[] toBytes(String hash) throws CommandException {
		if (hash.startsWith("0x") || hash.startsWith("0X"))
			hash = hash.substring(2);

		return Hex.fromHexString(hash, message -> new CommandException("The hexadecimal hash is invalid: " + message));
	}

	private static class RepresentationAnswer {
		@SuppressWarnings("unused")
		private String representation;
	}

	private static class RequestAnswer {
		@SuppressWarnings("unused")
		private String bytes;
	}
}