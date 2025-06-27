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

import java.security.NoSuchAlgorithmException;

import io.hotmoka.crypto.Hex;
import io.hotmoka.websockets.beans.api.InconsistentJsonException;
import io.hotmoka.websockets.beans.api.JsonRepresentation;
import io.mokamint.nonce.Challenges;
import io.mokamint.nonce.Prologs;
import io.mokamint.nonce.api.Deadline;
import io.mokamint.nonce.internal.DeadlineImpl;

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

	public Prologs.Json getProlog() {
		return prolog;
	}

	public long getProgressive() {
		return progressive;
	}

	public String getValue() {
		return value;
	}

	public Challenges.Json getChallenge() {
		return challenge;
	}

	public String getSignature() {
		return signature;
	}

	@Override
	public Deadline unmap() throws NoSuchAlgorithmException, InconsistentJsonException {
		return new DeadlineImpl(this);
	}
}