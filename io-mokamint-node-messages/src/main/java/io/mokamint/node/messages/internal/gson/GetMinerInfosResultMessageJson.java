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

package io.mokamint.node.messages.internal.gson;

import java.util.Optional;
import java.util.stream.Stream;

import io.hotmoka.websockets.beans.AbstractRpcMessageJsonRepresentation;
import io.hotmoka.websockets.beans.api.InconsistentJsonException;
import io.mokamint.node.MinerInfos;
import io.mokamint.node.messages.api.GetMinerInfosResultMessage;
import io.mokamint.node.messages.internal.GetMinerInfosResultMessageImpl;

/**
 * The JSON representation of a {@link GetMinerInfosResultMessage}.
 */
public abstract class GetMinerInfosResultMessageJson extends AbstractRpcMessageJsonRepresentation<GetMinerInfosResultMessage> {
	private final MinerInfos.Json[] miners;

	protected GetMinerInfosResultMessageJson(GetMinerInfosResultMessage message) {
		super(message);

		this.miners = message.get().map(MinerInfos.Json::new).toArray(MinerInfos.Json[]::new);
	}

	public Optional<Stream<MinerInfos.Json>> getMiners() {
		return miners == null ? Optional.empty() : Optional.of(Stream.of(miners));
	}

	@Override
	public GetMinerInfosResultMessage unmap() throws InconsistentJsonException {
		return new GetMinerInfosResultMessageImpl(this);
	}

	@Override
	protected String getExpectedType() {
		return GetMinerInfosResultMessage.class.getName();
	}
}