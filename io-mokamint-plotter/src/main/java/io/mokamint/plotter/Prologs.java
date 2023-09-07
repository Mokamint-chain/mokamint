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

package io.mokamint.plotter;

import java.io.IOException;
import java.security.NoSuchAlgorithmException;
import java.security.PublicKey;

import io.hotmoka.marshalling.api.UnmarshallingContext;
import io.mokamint.plotter.api.Prolog;
import io.mokamint.plotter.internal.PrologImpl;

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
	 */
	public static Prolog of(String chainId, PublicKey nodePublicKey, PublicKey plotPublicKey, byte[] extra) {
		return new PrologImpl(chainId, nodePublicKey, plotPublicKey, extra);
	}

	/**
	 * Unmarshals a prolog from the given context.
	 * 
	 * @param context the unmarshalling context
	 * @throws NoSuchAlgorithmException if the ed25519 signature algorithm is not available
	 * @throws IOException if the prolog could not be unmarshalled
	 */
	public static Prolog from(UnmarshallingContext context) throws NoSuchAlgorithmException, IOException {
		return new PrologImpl(context);
	}
}