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

import io.hotmoka.crypto.Hex;
import io.hotmoka.websockets.beans.api.InconsistentJsonException;
import io.hotmoka.websockets.beans.api.JsonRepresentation;
import io.mokamint.node.api.ChainInfo;
import io.mokamint.node.internal.ChainInfoImpl;

/**
 * The JSON representation of a {@link ChainInfo}.
 */
public abstract class ChainInfoJson implements JsonRepresentation<ChainInfo> {
	private final long length;
	private final String genesisHash;
	private final String headHash;
	private final String headStateId;

	protected ChainInfoJson(ChainInfo info) {
		this.length = info.getLength();
		this.genesisHash = info.getGenesisHash().map(Hex::toHexString).orElse(null);
		this.headHash = info.getHeadHash().map(Hex::toHexString).orElse(null);
		this.headStateId = info.getHeadStateId().map(Hex::toHexString).orElse(null);
	}

	public long getLength() {
		return length;
	}

	public String getGenesisHash() {
		return genesisHash;
	}

	public String getHeadHash() {
		return headHash;
	}

	public String getHeadStateId() {
		return headStateId;
	}

	@Override
	public ChainInfo unmap() throws InconsistentJsonException {
		return new ChainInfoImpl(this);
	}
}