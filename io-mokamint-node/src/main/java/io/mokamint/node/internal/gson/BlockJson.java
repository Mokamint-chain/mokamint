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
import java.security.spec.InvalidKeySpecException;
import java.util.stream.Stream;

import io.hotmoka.crypto.Base58ConversionException;
import io.hotmoka.crypto.Base64ConversionException;
import io.hotmoka.crypto.Hex;
import io.hotmoka.crypto.HexConversionException;
import io.hotmoka.exceptions.CheckSupplier;
import io.hotmoka.exceptions.UncheckFunction;
import io.hotmoka.websockets.beans.api.JsonRepresentation;
import io.mokamint.node.BlockDescriptions;
import io.mokamint.node.Transactions;
import io.mokamint.node.api.Block;
import io.mokamint.node.api.GenesisBlockDescription;
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
	private final String stateHash;
	private final String signature;

	protected BlockJson(Block block) {
		this.description = new BlockDescriptions.Json(block.getDescription());
		this.transactions = block.getTransactions().map(Transactions.Json::new).toArray(Transactions.Json[]::new);
		this.stateHash = Hex.toHexString(block.getStateHash());
		this.signature = Hex.toHexString(block.getSignature());
	}

	@Override
	public Block unmap() throws NoSuchAlgorithmException, InvalidKeySpecException, HexConversionException, Base64ConversionException, InvalidKeyException, Base58ConversionException {
		var description = this.description.unmap();

		Stream<Transaction> transactions = Stream.of(CheckSupplier.check(Base64ConversionException.class, () ->
			Stream.of(this.transactions).map(UncheckFunction.uncheck(Transactions.Json::unmap)).toArray(Transaction[]::new)
		));

		byte[] stateHash = Hex.fromHexString(this.stateHash);
		byte[] signature = Hex.fromHexString(this.signature);

		if (description instanceof GenesisBlockDescription gbd)
			return new GenesisBlockImpl(gbd, transactions, stateHash, signature);
		else
			return new NonGenesisBlockImpl((NonGenesisBlockDescription) description, transactions, stateHash, signature);
	}
}