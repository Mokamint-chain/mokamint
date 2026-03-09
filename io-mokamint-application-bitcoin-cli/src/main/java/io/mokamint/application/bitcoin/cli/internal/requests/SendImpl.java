/*
Copyright 2026 Fausto Spoto

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

package io.mokamint.application.bitcoin.cli.internal.requests;

import java.util.concurrent.TimeoutException;
import java.util.logging.Logger;

import io.hotmoka.cli.CommandException;
import io.mokamint.node.MisbehavingNodeException;
import io.mokamint.node.api.ClosedNodeException;
import io.mokamint.node.cli.AbstractPublicRpcCommand;
import io.mokamint.node.remote.api.RemotePublicNode;

public class SendImpl extends AbstractPublicRpcCommand {

	private final static Logger LOGGER = Logger.getLogger(SendImpl.class.getName());

	private void body(RemotePublicNode remote) throws CommandException, TimeoutException, InterruptedException, ClosedNodeException, MisbehavingNodeException {
		//if (from < -1L)
			//throw new CommandException("from cannot be smaller than -1!");

		//var info = remote.getChainInfo();
	}

	//@Parameters(description = "the Base64-encoded bytes of the request to add")
	//private String tx;

	/*private void body(RemotePublicNode remote) throws TimeoutException, InterruptedException, CommandException, ClosedNodeException {
		MempoolEntry info = addRequest(remote);

		if (json()) {
			try {
				System.out.println(new MempoolEntries.Encoder().encode(info));
			}
			catch (EncodeException e) {
				throw new CommandException("Cannot encode a mempool entry at \"" + publicUri() + "\" in JSON format!", e);
			}
		}
		else
			System.out.println(info);
	}

	private MempoolEntry addRequest(RemotePublicNode remote) throws TimeoutException, InterruptedException, ClosedNodeException, CommandException {
		try {
			return remote.add(Requests.of(Base64.fromBase64String(tx)));
		}
		catch (ApplicationTimeoutException e) {
			throw new CommandException("The application of the node timed out", e);
		}
		catch (Base64ConversionException e) {
			throw new CommandException("Illegal Base64 encoding of the request!", e);
		}
		catch (RequestRejectedException e) {
			throw new CommandException("The request has been rejected: " + e.getMessage(), e);
		}
	}*/

	@Override
	protected void execute() throws CommandException {
		execute(this::body);
	}
}