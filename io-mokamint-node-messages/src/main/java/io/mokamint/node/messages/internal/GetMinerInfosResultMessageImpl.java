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

package io.mokamint.node.messages.internal;

import java.util.Arrays;
import java.util.Objects;
import java.util.stream.Stream;

import io.hotmoka.websockets.beans.AbstractRpcMessage;
import io.hotmoka.websockets.beans.api.InconsistentJsonException;
import io.mokamint.node.MinerInfos;
import io.mokamint.node.api.MinerInfo;
import io.mokamint.node.api.PublicNode;
import io.mokamint.node.messages.api.GetMinerInfosResultMessage;
import io.mokamint.node.messages.internal.gson.GetMinerInfosResultMessageJson;

/**
 * Implementation of the network message corresponding to the result of the {@link PublicNode#getMinerInfos()} method.
 */
public class GetMinerInfosResultMessageImpl extends AbstractRpcMessage implements GetMinerInfosResultMessage {

	private final MinerInfo[] miners;

	/**
	 * Creates the message.
	 * 
	 * @param miners the miners in the message
	 * @param id the identifier of the message
	 */
	public GetMinerInfosResultMessageImpl(Stream<MinerInfo> miners, String id) {
		super(id);

		this.miners = miners
			.map(Objects::requireNonNull)
			.toArray(MinerInfo[]::new);
	}

	/**
	 * Creates a message from the given JSON representation.
	 * 
	 * @param json the JSON representation
	 * @throws InconsistentJsonException if {@code json} is inconsistent
	 */
	public GetMinerInfosResultMessageImpl(GetMinerInfosResultMessageJson json) throws InconsistentJsonException {
		super(json.getId());

		var maybeMiners = json.getMiners();
		if (maybeMiners.isEmpty())
			throw new InconsistentJsonException("miners must be specified");

		var miners = maybeMiners.get().toArray(MinerInfos.Json[]::new);
		this.miners = new MinerInfo[miners.length];
		for (int pos = 0; pos < miners.length; pos++) {
			var miner = miners[pos];
			if (miner == null)
				throw new InconsistentJsonException("miners cannot hold null elements");

			this.miners[pos] = miner.unmap();
		}
	}

	@Override
	public Stream<MinerInfo> get() {
		return Stream.of(miners);
	}

	@Override
	public boolean equals(Object other) {
		return other instanceof GetMinerInfosResultMessage gmrm && super.equals(other) && Arrays.equals(miners, gmrm.get().toArray(MinerInfo[]::new));
	}

	@Override
	protected String getExpectedType() {
		return GetMinerInfosResultMessage.class.getName();
	}
}