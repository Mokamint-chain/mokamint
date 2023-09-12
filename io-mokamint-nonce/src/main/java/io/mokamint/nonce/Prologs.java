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

package io.mokamint.nonce;

import java.io.IOException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.PublicKey;
import java.security.spec.InvalidKeySpecException;

import io.hotmoka.marshalling.api.UnmarshallingContext;
import io.mokamint.nonce.api.Prolog;
import io.mokamint.nonce.internal.PrologImpl;
import io.mokamint.nonce.internal.gson.PrologDecoder;
import io.mokamint.nonce.internal.gson.PrologEncoder;
import io.mokamint.nonce.internal.gson.PrologJson;

/**
 * Providers of plot prologs.
 */
public final class Prologs {

	private Prologs() {}

	/**
	 * Yields the prolog of a plot file.
	 * 
	 * @param chainId the chain identifier of the blockchain of the node using the plots with this prolog
	 * @param nodePublicKey the public key that the nodes, using this plots with this prolog,
	 *                      use to sign new mined blocks
	 * @param plotPublicKey the public key that identifies the plots with this prolog
	 * @param extra application-specific extra information
	 * @return the prolog
	 * @throws NoSuchAlgorithmException if the ed25519 signature algorithm is not available
	 * @throws InvalidKeyException if some of the keys is not an ed25519 valid public key
	 */
	public static Prolog of(String chainId, PublicKey nodePublicKey, PublicKey plotPublicKey, byte[] extra) throws InvalidKeyException, NoSuchAlgorithmException {
		return new PrologImpl(chainId, nodePublicKey, plotPublicKey, extra);
	}

	/**
	 * Yields the prolog of a plot file.
	 * 
	 * @param chainId the chain identifier of the blockchain of the node using the plots with this prolog
	 * @param nodePublicKeyBase58 the public key that the nodes, using this plots with this prolog,
	 *                            use to sign new mined blocks; in Base58 format
	 * @param plotPublicKeyBase58 the public key that identifies the plots with this prolog,
	 *                            in Base58 format
	 * @param extra application-specific extra information
	 * @return the prolog
	 * @throws NoSuchAlgorithmException if the ed25519 signature algorithm is not available
	 * @throws InvalidKeySpecException if some of the keys is not an ed25519 valid public key
	 */
	public static Prolog of(String chainId, String nodePublicKeyBase58, String plotPublicKeyBase58, byte[] extra) throws NoSuchAlgorithmException, InvalidKeySpecException {
		return new PrologImpl(chainId, nodePublicKeyBase58, plotPublicKeyBase58, extra);
	}

	/**
	 * Unmarshals a prolog from the given context.
	 * 
	 * @param context the unmarshalling context
	 * @return the prolog
	 * @throws NoSuchAlgorithmException if the ed25519 signature algorithm is not available
	 * @throws IOException if the prolog could not be unmarshalled
	 */
	public static Prolog from(UnmarshallingContext context) throws NoSuchAlgorithmException, IOException {
		return new PrologImpl(context);
	}

	/**
	 * Gson encoder.
	 */
	public static class Encoder extends PrologEncoder {}

	/**
	 * Gson decoder.
	 */
	public static class Decoder extends PrologDecoder {}

    /**
     * Json representation.
     */
	public static class Json extends PrologJson {

    	/**
    	 * Used by Gson.
    	 */
		public Json() {}

		/**
    	 * Creates the Json representation for the given prolog.
    	 * 
    	 * @param prolog the prolog
    	 */
    	public Json(Prolog prolog) {
    		super(prolog);
    	}
    }
}