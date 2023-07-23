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
import io.hotmoka.websockets.beans.api.JsonRepresentation;
import io.mokamint.nonce.Deadlines;
import io.mokamint.nonce.api.Deadline;

/**
 * The JSON representation of a {@link Deadline}.
 */
public abstract class DeadlineJson implements JsonRepresentation<Deadline> {
	private String prolog;
	private long progressive;
	private String value;
	private int scoopNumber;
	private String data;
	private String hashing;

	protected DeadlineJson(Deadline deadline) {
		this.prolog = Hex.toHexString(deadline.getProlog());
		this.progressive = deadline.getProgressive();
		this.value = Hex.toHexString(deadline.getValue());
		this.scoopNumber = deadline.getScoopNumber();
		this.data = Hex.toHexString(deadline.getData());
		this.hashing = deadline.getHashing().getName();
	}

	@Override
	public Deadline unmap() throws NoSuchAlgorithmException {
		return Deadlines.of(Hex.fromHexString(prolog), progressive, Hex.fromHexString(value), scoopNumber, Hex.fromHexString(data), HashingAlgorithms.mk(hashing, Function.identity()));
	}
}