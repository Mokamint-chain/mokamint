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

import java.util.Base64;

import io.hotmoka.websockets.beans.api.JsonRepresentation;
import io.mokamint.node.TransactionInfos;
import io.mokamint.node.api.TransactionInfo;

/**
 * The JSON representation of a {@link TransactionInfo}.
 */
public abstract class TransactionInfoJson implements JsonRepresentation<TransactionInfo> {

	/**
	 * The Base64-encoded hash of the transaction.
	 */
	private final String hash;

	/**
	 * The priority of the transaction.
	 */
	private final long priority;

	protected TransactionInfoJson(TransactionInfo info) {
		this.hash = Base64.getEncoder().encodeToString(info.getHash());
		this.priority = info.getPriority();
	}

	@Override
	public TransactionInfo unmap() {
		// TODO: exception
		return TransactionInfos.of(Base64.getDecoder().decode(hash), priority);
	}
}