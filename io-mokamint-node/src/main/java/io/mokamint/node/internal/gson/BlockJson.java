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

import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.SignatureException;
import java.util.stream.Stream;

import io.hotmoka.crypto.Hex;
import io.hotmoka.crypto.HexConversionException;
import io.hotmoka.websockets.beans.api.InconsistentJsonException;
import io.hotmoka.websockets.beans.api.JsonRepresentation;
import io.mokamint.node.BlockDescriptions;
import io.mokamint.node.Transactions;
import io.mokamint.node.api.Block;
import io.mokamint.node.api.GenesisBlockDescription;
import io.mokamint.node.api.NonGenesisBlock;
import io.mokamint.node.api.NonGenesisBlockDescription;
import io.mokamint.node.api.Transaction;
import io.mokamint.node.internal.GenesisBlockImpl;
import io.mokamint.node.internal.NonGenesisBlockImpl;

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
		this.transactions = block instanceof NonGenesisBlock ngb ? ngb.getTransactions().map(Transactions.Json::new).toArray(Transactions.Json[]::new) : new Transactions.Json[0];
		this.stateId = Hex.toHexString(block.getStateId());
		this.signature = Hex.toHexString(block.getSignature());
	}

	@Override
	public Block unmap() throws NoSuchAlgorithmException, InconsistentJsonException {
		try {
			var description = this.description.unmap();

			byte[] stateId = Hex.fromHexString(this.stateId);
			byte[] signature = Hex.fromHexString(this.signature);

			if (description instanceof GenesisBlockDescription gbd) {
				if (transactions.length != 0)
					throw new InconsistentJsonException("A genesis block cannot contain transactions");

				return new GenesisBlockImpl(gbd, stateId, signature);
			}
			else {
				var unmappedTransactions = new Transaction[transactions.length];
				for (int pos = 0; pos < unmappedTransactions.length; pos++)
					unmappedTransactions[pos] = transactions[pos].unmap();

				return new NonGenesisBlockImpl((NonGenesisBlockDescription) description, Stream.of(unmappedTransactions), stateId, signature);
			}
		}
		catch (HexConversionException | InvalidKeyException | SignatureException | NullPointerException | IllegalArgumentException e) {
			throw new InconsistentJsonException(e);
		}
	}
}