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

package io.mokamint.nonce.internal.gson;

import java.security.NoSuchAlgorithmException;
import java.util.function.Function;

import io.hotmoka.crypto.HashingAlgorithms;
import io.hotmoka.crypto.Hex;
import io.hotmoka.websockets.beans.JsonRepresentation;
import io.mokamint.nonce.DeadlineDescriptions;
import io.mokamint.nonce.api.DeadlineDescription;

/**
 * The JSON representation of a {@link DeadlineDescription}.
 */
public abstract class DeadlineDescriptionJson implements JsonRepresentation<DeadlineDescription> {
	private int scoopNumber;
	private String data;
	private String hashing;

	protected DeadlineDescriptionJson(DeadlineDescription description) {
		this.scoopNumber = description.getScoopNumber();
		this.data = Hex.toHexString(description.getData());
		this.hashing = description.getHashing().getName();
	}

	@Override
	public DeadlineDescription unmap() throws NoSuchAlgorithmException {
		return DeadlineDescriptions.of(scoopNumber, Hex.fromHexString(data), HashingAlgorithms.mk(hashing, Function.identity()));
	}
}