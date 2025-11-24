/*
Copyright 2025 Fausto Spoto

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

package io.mokamint.node.cli.internal.chain.json;

import io.hotmoka.websockets.beans.api.InconsistentJsonException;
import io.hotmoka.websockets.beans.api.JsonRepresentation;
import io.mokamint.node.ChainInfos;
import io.mokamint.node.cli.api.chain.ChainInfoOutput;
import io.mokamint.node.cli.internal.chain.InfoImpl;

/**
 * The JSON representation of the output of the {@code mokamint-node chain info} command.
 */
public abstract class ChainInfoOutputJson implements JsonRepresentation<ChainInfoOutput> {
	private final ChainInfos.Json chainInfo;

	protected ChainInfoOutputJson(ChainInfoOutput output) {
		this.chainInfo = new ChainInfos.Json(output.getChainInfo());
	}

	@Override
	public ChainInfoOutput unmap() throws InconsistentJsonException {
		return new InfoImpl.Output(this);
	}

	public ChainInfos.Json getInfo() {
		return chainInfo;
	}
}