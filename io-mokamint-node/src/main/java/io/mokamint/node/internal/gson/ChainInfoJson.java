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

package io.mokamint.node.internal.gson;

import java.util.Optional;

import io.hotmoka.crypto.Hex;
import io.hotmoka.crypto.HexConversionException;
import io.hotmoka.websockets.beans.api.InconsistentJsonException;
import io.hotmoka.websockets.beans.api.JsonRepresentation;
import io.mokamint.node.ChainInfos;
import io.mokamint.node.api.ChainInfo;

/**
 * The JSON representation of a {@link ChainInfo}.
 */
public abstract class ChainInfoJson implements JsonRepresentation<ChainInfo> {
	private final long height;
	private final String genesisHash;
	private final String headHash;
	private final String headStateId;

	protected ChainInfoJson(ChainInfo info) {
		this.height = info.getLength();
		var genesisHash = info.getGenesisHash();
		this.genesisHash = genesisHash.isEmpty() ? null : Hex.toHexString(genesisHash.get());
		var headHash = info.getHeadHash();
		this.headHash = headHash.isEmpty() ? null : Hex.toHexString(headHash.get());
		var headStateId = info.getHeadStateId();
		this.headStateId = headStateId.isEmpty() ? null : Hex.toHexString(headStateId.get());
	}

	@Override
	public ChainInfo unmap() throws InconsistentJsonException {
		try {
			return ChainInfos.of(height,
				genesisHash == null ? Optional.empty() : Optional.of(Hex.fromHexString(genesisHash)),
				headHash == null ? Optional.empty() : Optional.of(Hex.fromHexString(headHash)),
				headStateId == null ? Optional.empty() : Optional.of(Hex.fromHexString(headStateId)));
		}
		catch (HexConversionException e) {
			throw new InconsistentJsonException(e);
		}
	}
}