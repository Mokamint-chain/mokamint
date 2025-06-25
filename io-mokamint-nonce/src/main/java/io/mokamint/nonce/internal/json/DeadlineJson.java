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

package io.mokamint.nonce.internal.json;

import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.SignatureException;

import io.hotmoka.crypto.Hex;
import io.hotmoka.crypto.HexConversionException;
import io.hotmoka.websockets.beans.api.InconsistentJsonException;
import io.hotmoka.websockets.beans.api.JsonRepresentation;
import io.mokamint.nonce.Challenges;
import io.mokamint.nonce.Deadlines;
import io.mokamint.nonce.Prologs;
import io.mokamint.nonce.api.Deadline;

/**
 * The JSON representation of a {@link Deadline}.
 */
public abstract class DeadlineJson implements JsonRepresentation<Deadline> {
	private final Prologs.Json prolog;
	private final long progressive;
	private final String value;
	private final Challenges.Json challenge;
	private final String signature;

	protected DeadlineJson(Deadline deadline) {
		this.prolog = new Prologs.Json(deadline.getProlog());
		this.progressive = deadline.getProgressive();
		this.value = Hex.toHexString(deadline.getValue());
		this.challenge = new Challenges.Json(deadline.getChallenge());
		this.signature = Hex.toHexString(deadline.getSignature());
	}

	@Override
	public Deadline unmap() throws NoSuchAlgorithmException, InconsistentJsonException {
		try {
			return Deadlines.of(prolog.unmap(), progressive, Hex.fromHexString(value), challenge.unmap(), Hex.fromHexString(signature));
		}
		catch (NullPointerException | IllegalArgumentException | HexConversionException | InvalidKeyException | SignatureException e) {
			throw new InconsistentJsonException(e);
		}
	}
}