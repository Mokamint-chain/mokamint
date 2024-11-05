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
import java.util.stream.Stream;

import io.hotmoka.crypto.Hex;
import io.hotmoka.websockets.beans.api.InconsistentJsonException;
import io.hotmoka.websockets.beans.api.JsonRepresentation;
import io.mokamint.node.api.ChainPortion;
import io.mokamint.node.internal.ChainPortionImpl;

/**
 * The JSON representation of a {@link ChainPortion}.
 */
public abstract class ChainPortionJson implements JsonRepresentation<ChainPortion> {
	private final String[] hashes;

	protected ChainPortionJson(ChainPortion chain) {
		this.hashes = chain.getHashes().map(Hex::toHexString).toArray(String[]::new);
	}

	public Optional<Stream<String>> getHashes() {
		return Optional.ofNullable(hashes).map(Stream::of);
	}

	@Override
	public ChainPortion unmap() throws InconsistentJsonException {
		return new ChainPortionImpl(this);
	}
}