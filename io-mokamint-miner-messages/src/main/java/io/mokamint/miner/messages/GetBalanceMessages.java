/*
Copyright 2025 Fausto Spoto

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

package io.mokamint.miner.messages;

import java.security.PublicKey;

import io.hotmoka.crypto.api.SignatureAlgorithm;
import io.hotmoka.websockets.beans.MappedDecoder;
import io.hotmoka.websockets.beans.MappedEncoder;
import io.mokamint.miner.messages.api.GetBalanceMessage;
import io.mokamint.miner.messages.internal.GetBalanceMessageImpl;
import io.mokamint.miner.messages.internal.json.GetBalanceMessageJson;

/**
 * A provider of {@link GetBalanceMessage}.
 */
public final class GetBalanceMessages {

	private GetBalanceMessages() {}

	/**
	 * Yields a {@link GetBalanceMessage}.
	 * 
	 * @param signature the signature algorithm of {@code key}
	 * @param publicKey the public key whose balance is required
	 * @param id the identifier of the message
	 * @return the message
	 */
	public static GetBalanceMessage of(SignatureAlgorithm signature, PublicKey publicKey, String id) {
		return new GetBalanceMessageImpl(signature, publicKey, id);
	}

	/**
	 * Gson encoder.
	 */
	public static class Encoder extends MappedEncoder<GetBalanceMessage, Json> {

		/**
		 * Creates a new encoder.
		 */
		public Encoder() {
			super(Json::new);
		}
	}

	/**
	 * Gson decoder.
	 */
	public static class Decoder extends MappedDecoder<GetBalanceMessage, Json> {

		/**
		 * Creates a new decoder.
		 */
		public Decoder() {
			super(Json.class);
		}
	}

	/**
     * Json representation.
     */
    public static class Json extends GetBalanceMessageJson {

    	/**
    	 * Creates the Json representation for the given message.
    	 * 
    	 * @param message the message
    	 */
    	public Json(GetBalanceMessage message) {
    		super(message);
    	}
    }
}