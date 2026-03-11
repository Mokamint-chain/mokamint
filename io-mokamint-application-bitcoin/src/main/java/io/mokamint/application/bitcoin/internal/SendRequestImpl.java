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

package io.mokamint.application.bitcoin.internal;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.math.BigInteger;
import java.security.InvalidKeyException;
import java.security.KeyPair;
import java.security.NoSuchAlgorithmException;
import java.security.PublicKey;
import java.security.SignatureException;
import java.security.spec.InvalidKeySpecException;
import java.util.Objects;

import io.hotmoka.crypto.Base58;
import io.hotmoka.crypto.Hex;
import io.hotmoka.crypto.SignatureAlgorithms;
import io.hotmoka.crypto.api.SignatureAlgorithm;
import io.hotmoka.marshalling.AbstractMarshallable;
import io.hotmoka.marshalling.UnmarshallingContexts;
import io.hotmoka.marshalling.api.MarshallingContext;
import io.hotmoka.marshalling.api.UnmarshallingContext;
import io.mokamint.application.ApplicationException;
import io.mokamint.application.bitcoin.SendRequest;

/**
 * The implementation of the request for sending an amount of coins
 * from a sender to a receiver.
 */
public class SendRequestImpl extends AbstractMarshallable implements SendRequest {
	
	/**
	 * The public key of the sender.
	 */
	private final PublicKey publicKeyOfSender;

	/**
	 * The base58 encoding of {@link #publicKeyOfSender}.
	 */
	private final String publicKeyOfSenderBase58;

	/**
	 * The amount of coins to send.
	 */
	private final BigInteger amount;

	/**
	 * The public key of the receiver.
	 */
	private final PublicKey publicKeyOfReceiver;

	/**
	 * The base58 encoding of {@link #publicKeyOfReceiver}.
	 */
	private final String publicKeyOfReceiverBase58;

	/**
	 * A progressive nonce used to distinguish repeated requests.
	 */
	private final long nonce;

	/**
	 * The signature of the request.
	 */
	private final byte[] signature;

	/**
	 * The algorithm used for signing the request. Both
	 * {@link #publicKeyOfSender} and {@link #publicKeyOfReceiver} are for this algorithm.
	 */
	private final static SignatureAlgorithm ed25519 = ed25519();

	/**
	 * Builds the request.
	 * 
	 * @param keysOfSender the key pair of the sender, for the ed25519 signature algorithm
	 * @param amount the amount of coins to send
	 * @param publicKeyOfReceiver the public key of the receiver, for the ed25519 signature algorithm
	 * @param nonce a progressive nonce used to distinguish repeated requests
	 * @throws InvalidKeyException if one of the keys is invalid for the ed25519 signature algorithm
	 * @throws SignatureException if the request could not be signed
	 */
	public SendRequestImpl(KeyPair keysOfSender, BigInteger amount, PublicKey publicKeyOfReceiver, long nonce) throws InvalidKeyException, SignatureException {
		this.publicKeyOfSender = keysOfSender.getPublic();
		this.amount = Objects.requireNonNull(amount);
		this.publicKeyOfReceiver = publicKeyOfReceiver;
		// the following enforces the validity of the public keys
		this.publicKeyOfSenderBase58 = Base58.toBase58String(ed25519.encodingOf(publicKeyOfSender));
		this.publicKeyOfReceiverBase58 = Base58.toBase58String(ed25519.encodingOf(publicKeyOfReceiver));
		this.nonce = nonce;
		this.signature = ed25519.getSigner(keysOfSender.getPrivate(), SendRequestImpl::toByteArrayWithoutSignature).sign(this);
	}

	/**
	 * Reconstructs a request from the given marshalled bytes.
	 * 
	 * @param bytes the marshalled bytes
	 * @throws IOException if unmarshalling from {@code bytes} fails
	 */
	public static SendRequestImpl from(byte[] bytes) throws IOException {
		try (var context = UnmarshallingContexts.of(new ByteArrayInputStream(bytes))) {
			return new SendRequestImpl(context);
		}
	}

	/**
	 * Unmarshals a request from the given stream.
	 * 
	 * @param context the unmarshalling context
	 * @throws IOException if the message cannot be unmarshalled
	 */
	private SendRequestImpl(UnmarshallingContext context) throws IOException {
		var publicKeyLength = ed25519.publicKeyLength().getAsInt();
		int signatureLength = ed25519.length().getAsInt();

		try {
			byte[] publicKeyOfSenderEncoding = context.readBytes(publicKeyLength, "Expected " + publicKeyLength + " bytes for the public key of the sender");
			this.publicKeyOfSender = ed25519.publicKeyFromEncoding(publicKeyOfSenderEncoding);
			this.publicKeyOfSenderBase58 = Base58.toBase58String(publicKeyOfSenderEncoding);
			this.amount = context.readBigInteger();
			byte[] publicKeyOfReceiverEncoding = context.readBytes(publicKeyLength, "Expected " + publicKeyLength + " bytes for the public key of the receiver");
			this.publicKeyOfReceiver = ed25519.publicKeyFromEncoding(publicKeyOfReceiverEncoding);
			this.publicKeyOfReceiverBase58 = Base58.toBase58String(publicKeyOfReceiverEncoding);
			this.nonce = context.readLong();
			this.signature = context.readBytes(signatureLength, "Expected " + signatureLength + " bytes for the signature");
			ed25519.getVerifier(publicKeyOfSender, SendRequestImpl::toByteArrayWithoutSignature).verify(this, signature);
		}
		catch (SignatureException e) {
			throw new IOException("Invalid signature: " + e.getMessage());
		}
		catch (InvalidKeyException | InvalidKeySpecException e) {
			throw new IOException("Invalid public key: " + e.getMessage());
		}
	}

	/**
	 * Writes this request into the given marshalling context, without the signature field.
	 * 
	 * @param context the marshalling context
	 * @throws IOException if marshalling failed
	 */
	private void intoWithoutSignature(MarshallingContext context) throws IOException {
		try {
			context.writeBytes(ed25519.encodingOf(publicKeyOfSender));
			context.writeBigInteger(amount);
			context.writeBytes(ed25519.encodingOf(publicKeyOfReceiver));
			context.writeLong(nonce);
		}
		catch (InvalidKeyException e) {
			// the two constructors enforce the validity of the keys, therefore this should be impossible
			throw new ApplicationException(e);
		}
	}

	@Override
	public PublicKey getPublicKeyOfSender() {
		return publicKeyOfSender;
	}

	@Override
	public PublicKey getPublicKeyOfReceiver() {
		return publicKeyOfReceiver;
	}

	@Override
	public BigInteger getAmount() {
		return amount;
	}

	@Override
	public long getNonce() {
		return nonce;
	}

	@Override
	public void into(MarshallingContext context) throws IOException {
		intoWithoutSignature(context);
		context.writeBytes(signature);
	}

	@Override
	public String toString() {
		return publicKeyOfSenderBase58 + " sends " + amount + " to " + publicKeyOfReceiverBase58 + " with nonce = " + nonce + " and signature = " + Hex.toHexString(signature);
	}

	@Override
	public boolean equals(Object other) {
		return other instanceof SendRequest sr
				&& publicKeyOfSender.equals(sr.getPublicKeyOfSender())
				&& publicKeyOfReceiver.equals(sr.getPublicKeyOfReceiver())
				&& amount.equals(sr.getAmount())
				&& nonce == sr.getNonce();
	}

	@Override
	public int hashCode() {
		return publicKeyOfSender.hashCode() ^ publicKeyOfReceiver.hashCode() ^ amount.hashCode() ^ Long.hashCode(nonce);
	}

	/**
	 * Transforms this request into a byte array, without the signature field.
	 * 
	 * @return the resulting byte array
	 */
	private byte[] toByteArrayWithoutSignature() {
		try (var baos = new ByteArrayOutputStream(); var context = createMarshallingContext(baos)) {
			intoWithoutSignature(context);
			context.flush();
			return baos.toByteArray();
		}
		catch (IOException e) {
			// impossible with a ByteArrayOutputStream
			throw new UncheckedIOException("Unexpected exception", e);
		}
	}

	private static SignatureAlgorithm ed25519() {
		try {
			return SignatureAlgorithms.ed25519();
		}
		catch (NoSuchAlgorithmException e) {
			throw new ApplicationException(e);
		}
	}
}