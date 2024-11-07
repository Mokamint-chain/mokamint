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

import java.security.NoSuchAlgorithmException;
import java.util.stream.Stream;

import io.hotmoka.crypto.Hex;
import io.hotmoka.websockets.beans.api.InconsistentJsonException;
import io.hotmoka.websockets.beans.api.JsonRepresentation;
import io.mokamint.node.BlockDescriptions;
import io.mokamint.node.Transactions;
import io.mokamint.node.api.Block;
import io.mokamint.node.api.NonGenesisBlock;
import io.mokamint.node.internal.AbstractBlock;

/**
 * The JSON representation of a {@link Block}.
 */
public abstract class BlockJson implements JsonRepresentation<Block> {
	private final BlockDescriptions.Json description;
	private final Transactions.Json[] transactions;
	private final String stateId;
	private final String signature;

	protected BlockJson(Block block) {
		this.description = new BlockDescriptions.Json(block.getDescription());
		this.transactions = block instanceof NonGenesisBlock ngb ? ngb.getTransactions().map(Transactions.Json::new).toArray(Transactions.Json[]::new) : null;
		this.stateId = Hex.toHexString(block.getStateId());
		this.signature = Hex.toHexString(block.getSignature());
	}

	public BlockDescriptions.Json getDescription() {
		return description;
	}

	public String getStateId() {
		return stateId;
	}

	public String getSignature() {
		return signature;
	}

	public Stream<Transactions.Json> getTransactions() {
		return transactions == null ? Stream.empty() : Stream.of(transactions);
	}

	@Override
	public Block unmap() throws NoSuchAlgorithmException, InconsistentJsonException {
		return AbstractBlock.from(this);
	}
}