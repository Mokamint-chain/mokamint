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

package io.mokamint.node;

import java.io.IOException;

import io.hotmoka.marshalling.api.UnmarshallingContext;
import io.mokamint.node.api.Transaction;
import io.mokamint.node.internal.TransactionImpl;

/**
 * Providers of transaction objects.
 */
public abstract class Transactions {

	private Transactions() {}

	/**
	 * Yields a new transaction object.
	 * 
	 * @param bytes the bytes of the transaction
	 * @return the transaction object
	 */
	public static Transaction of(byte[] bytes) {
		return new TransactionImpl(bytes);
	}

	/**
	 * Unmarshals a transaction from the given context.
	 * 
	 * @param context the context
	 * @return the transaction
	 * @throws IOException if the transaction cannot be unmarshalled
	 */
	public static Transaction from(UnmarshallingContext context) throws IOException {
		return new TransactionImpl(context);
	}
}