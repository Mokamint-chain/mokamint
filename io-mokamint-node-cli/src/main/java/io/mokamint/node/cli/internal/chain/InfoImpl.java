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

package io.mokamint.node.cli.internal.chain;

import java.security.NoSuchAlgorithmException;
import java.util.concurrent.TimeoutException;

import io.hotmoka.annotations.Immutable;
import io.hotmoka.cli.CommandException;
import io.hotmoka.exceptions.Objects;
import io.hotmoka.websockets.beans.api.InconsistentJsonException;
import io.mokamint.node.api.ChainInfo;
import io.mokamint.node.api.ClosedNodeException;
import io.mokamint.node.cli.ChainInfoOutputs;
import io.mokamint.node.cli.api.chain.ChainInfoOutput;
import io.mokamint.node.cli.internal.AbstractPublicRpcCommand;
import io.mokamint.node.cli.internal.chain.json.ChainInfoOutputJson;
import io.mokamint.node.remote.api.RemotePublicNode;

public class InfoImpl extends AbstractPublicRpcCommand {

	private void body(RemotePublicNode remote) throws TimeoutException, InterruptedException, CommandException, ClosedNodeException {
		report(new Output(remote.getChainInfo()), ChainInfoOutputs.Encoder::new);
	}

	@Override
	protected void execute() throws CommandException {
		execute(this::body);
	}

	/**
	 * The output of this command.
	 */
	@Immutable
	public static class Output implements ChainInfoOutput {
		private final ChainInfo chainInfo;

		/**
		 * Builds the output of the command from its JSON representation.
		 * 
		 * @param json the JSON representation
		 * @throws InconsistentJsonException if {@code json} is inconsistent
		 * @throws NoSuchAlgorithmException if the JSON refers to an unavailable cryptographic algorithm
		 */
		public Output(ChainInfoOutputJson json) throws InconsistentJsonException {
			this.chainInfo = Objects.requireNonNull(json.getInfo(), "chainInfo cannot be null", InconsistentJsonException::new).unmap();
		}

		private Output(ChainInfo chainInfo) {
			this.chainInfo = chainInfo;
		}

		@Override
		public ChainInfo getChainInfo() {
			return chainInfo;
		}

		@Override
		public String toString() {
			return chainInfo.toString();
		}
	}
}