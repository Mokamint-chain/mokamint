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
import java.security.spec.InvalidKeySpecException;

import io.hotmoka.crypto.Base58;
import io.hotmoka.crypto.Base58ConversionException;
import io.hotmoka.crypto.api.SignatureAlgorithm;
import picocli.CommandLine.ITypeConverter;

/**
 * A converter of a Base58-encoded string option into a {@link PublicKey}.
 */
public abstract class PublicKeyOptionConverter implements ITypeConverter<PublicKey> {

	/**
	 * Yields the signature algorithm to use for the public key.
	 * 
	 * @return the signature algorithm
	 * @throws NoSuchAlgorithmException if the signature algorithm is not available
	 */
	protected abstract SignatureAlgorithm signature() throws NoSuchAlgorithmException;

	@Override
	public PublicKey convert(String value) throws Base58ConversionException, InvalidKeySpecException, NoSuchAlgorithmException {
		return signature().publicKeyFromEncoding(Base58.fromBase58String(value));
	}
}