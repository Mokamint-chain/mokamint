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

import io.hotmoka.crypto.HashingAlgorithms;
import io.hotmoka.crypto.Hex;
import io.hotmoka.crypto.HexConversionException;
import io.hotmoka.websockets.beans.api.InconsistentJsonException;
import io.hotmoka.websockets.beans.api.JsonRepresentation;
import io.mokamint.nonce.Deadlines;
import io.mokamint.nonce.Prologs;
import io.mokamint.nonce.api.Deadline;

/**
 * The JSON representation of a {@link Deadline}.
 */
public abstract class DeadlineJson implements JsonRepresentation<Deadline> {
	private Prologs.Json prolog;
	private long progressive;
	private String value;
	private int scoopNumber;
	private String data;
	private String hashing;
	private String signature;

	/**
	 * Used by Gson.
	 */
	protected DeadlineJson() {}

	protected DeadlineJson(Deadline deadline) {
		this.prolog = new Prologs.Json(deadline.getProlog());
		this.progressive = deadline.getProgressive();
		this.value = Hex.toHexString(deadline.getValue());
		this.scoopNumber = deadline.getScoopNumber();
		this.data = Hex.toHexString(deadline.getGenerationSignature());
		this.hashing = deadline.getHashing().getName();
		this.signature = Hex.toHexString(deadline.getSignature());
	}

	@Override
	public Deadline unmap() throws NoSuchAlgorithmException, InconsistentJsonException {
		try {
			return Deadlines.of(prolog.unmap(), progressive, Hex.fromHexString(value), scoopNumber,
				Hex.fromHexString(data), HashingAlgorithms.of(hashing), Hex.fromHexString(signature));
		}
		catch (NullPointerException | IllegalArgumentException | HexConversionException e) {
			throw new InconsistentJsonException(e);
		}
	}
}