/*
Copyright 2026 Fausto Spoto

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

package io.mokamint.node.cli.converters;

import java.security.NoSuchAlgorithmException;
import java.security.PublicKey;

import io.hotmoka.crypto.SignatureAlgorithms;
import io.hotmoka.crypto.api.SignatureAlgorithm;

/**
 * A converter of a Base58-encoded string option into an ED25519 {@link PublicKey}.
 */
public class ED25519PublicKeyOptionConverter extends PublicKeyOptionConverter {

	/**
	 * Builds the converter.
	 */
	public ED25519PublicKeyOptionConverter() {}

	@Override
	protected SignatureAlgorithm signature() throws NoSuchAlgorithmException {
		return SignatureAlgorithms.ed25519();
	}
}