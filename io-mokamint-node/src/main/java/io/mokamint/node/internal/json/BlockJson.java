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

package io.mokamint.node.internal.json;

import java.security.NoSuchAlgorithmException;
import java.util.stream.Stream;

import io.hotmoka.crypto.Hex;
import io.hotmoka.websockets.beans.api.InconsistentJsonException;
import io.hotmoka.websockets.beans.api.JsonRepresentation;
import io.mokamint.node.BlockDescriptions;
import io.mokamint.node.api.Block;
import io.mokamint.node.api.NonGenesisBlock;
import io.mokamint.node.api.Request;
import io.mokamint.node.internal.AbstractBlock;

/**
 * The JSON representation of a {@link Block}.
 */
public abstract class BlockJson implements JsonRepresentation<Block> {
	private final BlockDescriptions.Json description;
	private final String[] transactions;
	private final String stateId;
	private final String signature;

	protected BlockJson(Block block) {
		this.description = new BlockDescriptions.Json(block.getDescription());
		this.transactions = block instanceof NonGenesisBlock ngb ? ngb.getRequests().map(Request::toBase64String).toArray(String[]::new) : null;
		this.stateId = Hex.toHexString(block.getStateId());
		this.signature = Hex.toHexString(block.getSignature());
	}

	/**
	 * Yields the description of this block.
	 * 
	 * @return the description
	 */
	public BlockDescriptions.Json getDescription() {
		return description;
	}

	/**
	 * Yields the identifier of the state of the application at the end of the execution of the
	 * transactions from the beginning of the blockchain to the end of this block.
	 * 
	 * @return the identifier of the state of the application, as a hexadecimal string
	 */
	public String getStateId() {
		return stateId;
	}

	/**
	 * Yields the signature of this block, computed from its hash by the node
	 * that mined this block. This signature must have been computed with the
	 * private key corresponding to the node's public key, which is inside the prolog
	 * of the deadline for non-genesis blocks or inside the genesis blocks themselves.
	 * 
	 * @return the signature, as a hexadecimal string
	 */
	public String getSignature() {
		return signature;
	}

	/**
	 * Yields the requests inside this block.
	 * 
	 * @return the requests
	 */
	public Stream<String> getRequests() {
		return transactions == null ? Stream.empty() : Stream.of(transactions);
	}

	@Override
	public Block unmap() throws NoSuchAlgorithmException, InconsistentJsonException {
		return AbstractBlock.from(this);
	}
}