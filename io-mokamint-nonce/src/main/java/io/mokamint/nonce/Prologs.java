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

import io.hotmoka.crypto.api.SignatureAlgorithm;
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
	 * @param signatureForBlocks the signature algorithm that nodes must use to sign the
	 *                           blocks having the deadline with the prolog, with {@code publicKeyForSigningBlocks}
	 * @param publicKeyForSigningBlocks the public key that the nodes must use to sign the
	 *                                  blocks having a deadline with the prolog
	 * @param signatureForDeadlines the signature algorithm that miners must use to sign
	 *                              the deadlines with this prolog, with {@code publicKeyForSigningDeadlines}
	 * @param publicKeyForSigningDeadlines the public key that miners must use to sign the deadlines with the prolog
	 * @param extra application-specific extra information
	 * @return the prolog
	 * @throws InvalidKeyException if some of the keys is not valid
	 */
	public static Prolog of(String chainId, SignatureAlgorithm signatureForBlocks, PublicKey publicKeyForSigningBlocks,
			SignatureAlgorithm signatureForDeadlines, PublicKey publicKeyForSigningDeadlines, byte[] extra)
					throws InvalidKeyException {
		return new PrologImpl(chainId, signatureForBlocks, publicKeyForSigningBlocks, signatureForDeadlines, publicKeyForSigningDeadlines, extra);
	}

	/**
	 * Unmarshals a prolog from the given context. It assumes that the prolog was previously marshalled through
	 * {@link Prolog#intoWithoutConfigurationData(io.hotmoka.marshalling.api.MarshallingContext)}.
	 * 
	 * @param context the unmarshalling context
	 * @param chainId the chain identifier of the node storing the prolog
	 * @param signatureForBlocks the signature algorithm for the blocks
	 * @param signatureForDeadlines the signature algorithm for the deadlines
	 * @return the prolog
	 * @throws IOException if the prolog could not be unmarshalled
	 */
	public static Prolog from(UnmarshallingContext context, String chainId, SignatureAlgorithm signatureForBlocks, SignatureAlgorithm signatureForDeadlines) throws IOException {
		return new PrologImpl(context, chainId, signatureForBlocks, signatureForDeadlines);
	}

	/**
	 * Unmarshals a prolog from the given context. It assumes that the prolog was previously marshalled through
	 * {@link Prolog#into(io.hotmoka.marshalling.api.MarshallingContext)}.
	 * 
	 * @param context the unmarshalling context
	 * @return the prolog
	 * @throws NoSuchAlgorithmException if some signature algorithm is not available
	 * @throws IOException if the prolog could not be unmarshalled
	 */
	public static Prolog from(UnmarshallingContext context) throws NoSuchAlgorithmException, IOException {
		return new PrologImpl(context);
	}

	/**
	 * Gson encoder.
	 */
	public static class Encoder extends PrologEncoder {

		/**
		 * Creates a new encoder.
		 */
		public Encoder() {}
	}

	/**
	 * Gson decoder.
	 */
	public static class Decoder extends PrologDecoder {

		/**
		 * Creates a new decoder.
		 */
		public Decoder() {}
	}

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