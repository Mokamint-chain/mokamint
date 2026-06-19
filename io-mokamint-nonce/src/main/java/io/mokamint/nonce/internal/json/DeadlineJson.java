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
	private final String extra;

	protected DeadlineJson(Deadline deadline) {
		this.prolog = new Prologs.Json(deadline.getProlog());
		this.progressive = deadline.getProgressive();
		this.value = Hex.toHexString(deadline.getValue());
		this.challenge = new Challenges.Json(deadline.getChallenge());
		this.extra = Hex.toHexString(deadline.getExtra());
	}

	/**
	 * Yields the prolog that was used to create the nonce from which
	 * this deadline has been generated.
	 * 
	 * @return the prolog
	 */
	public Prologs.Json getProlog() {
		return prolog;
	}

	/**
	 * Yields the progressive number of the nonce of the deadline.
	 * 
	 * @return the progressive number
	 */
	public long getProgressive() {
		return progressive;
	}

	/**
	 * Yields the value of the deadline.
	 * 
	 * @return the value
	 */
	public String getValue() {
		return value;
	}

	/**
	 * Yields the challenge this deadline responds to.
	 * 
	 * @return the challenge this deadline responds to
	 */
	public Challenges.Json getChallenge() {
		return challenge;
	}

	/**
	 * Yields the application-specific data of the deadline.
	 * 
	 * @return the application-specific data of the deadline
	 */
	public String getExtra() {
		return extra;
	}

	@Override
	public Deadline unmap() throws NoSuchAlgorithmException, InconsistentJsonException {
		return new DeadlineImpl(this);
	}
}