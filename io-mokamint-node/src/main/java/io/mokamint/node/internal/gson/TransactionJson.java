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

import io.hotmoka.crypto.Base64;
import io.hotmoka.crypto.Base64ConversionException;
import io.hotmoka.websockets.beans.api.InconsistentJsonException;
import io.hotmoka.websockets.beans.api.JsonRepresentation;
import io.mokamint.node.Transactions;
import io.mokamint.node.api.Transaction;

/**
 * The JSON representation of a {@link Transaction}.
 */
public abstract class TransactionJson implements JsonRepresentation<Transaction> {

	/**
	 * The Base64-encoded bytes of the transaction.
	 */
	private final String bytes;

	protected TransactionJson(Transaction transaction) {
		this.bytes = Base64.toBase64String(transaction.getBytes());
	}

	@Override
	public Transaction unmap() throws InconsistentJsonException {
		try {
			return Transactions.of(Base64.fromBase64String(bytes));
		}
		catch (Base64ConversionException | NullPointerException e) {
			throw new InconsistentJsonException(e);
		}
	}
}