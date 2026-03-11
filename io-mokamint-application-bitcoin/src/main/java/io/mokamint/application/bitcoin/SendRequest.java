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

package io.mokamint.application.bitcoin;

import java.io.IOException;
import java.math.BigInteger;
import java.security.InvalidKeyException;
import java.security.KeyPair;
import java.security.PublicKey;
import java.security.SignatureException;

import io.hotmoka.marshalling.api.Marshallable;
import io.mokamint.application.bitcoin.internal.SendRequestImpl;

/**
 * A request for sending an amount of coins from a sender to a receiver.
 */
public interface SendRequest extends Marshallable {
	
	/**
	 * Yields a request.
	 * 
	 * @param keysOfSender the key pair of the sender, for the ed25519 signature algorithm
	 * @param amount the amount of coins to send
	 * @param publicKeyOfReceiver the public key of the receiver, for the ed25519 signature algorithm
	 * @return the request
	 * @throws InvalidKeyException if one of the keys is invalid for the ed25519 signature algorithm
	 * @throws SignatureException if the request could not be signed
	 */
	static SendRequest of(KeyPair keysOfSender, BigInteger amount, PublicKey publicKeyOfReceiver) throws InvalidKeyException, SignatureException {
		return new SendRequestImpl(keysOfSender, amount, publicKeyOfReceiver);
	}

	/**
	 * Reconstructs a request from the given marshalled bytes.
	 * 
	 * @param bytes the marshalled bytes
	 * @throws IOException if unmarshalling from {@code bytes} fails
	 */
	static SendRequest from(byte[] bytes) throws IOException {
		return SendRequestImpl.from(bytes);
	}

	/**
	 * Yields the public key of the sender.
	 * 
	 * @return the public key of the sender
	 */
	PublicKey getPublicKeyOfSender();

	/**
	 * Yields the public key of the receiver.
	 * 
	 * @return the public key of the receiver
	 */
	PublicKey getPublicKeyOfReceiver();

	/**
	 * Yields the amount of coins to send.
	 * 
	 * @return the amount of coins to send
	 */
	BigInteger getAmount();
}