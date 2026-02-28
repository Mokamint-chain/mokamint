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

package io.mokamint.node.internal;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.security.InvalidKeyException;
import java.security.PrivateKey;
import java.security.SignatureException;
import java.util.Arrays;
import java.util.Objects;
import java.util.stream.Stream;

import io.hotmoka.annotations.Immutable;
import io.hotmoka.crypto.Base64;
import io.hotmoka.marshalling.api.MarshallingContext;
import io.hotmoka.marshalling.api.UnmarshallingContext;
import io.hotmoka.websockets.beans.api.InconsistentJsonException;
import io.mokamint.node.Requests;
import io.mokamint.node.api.NonGenesisBlock;
import io.mokamint.node.api.NonGenesisBlockDescription;
import io.mokamint.node.api.Request;
import io.mokamint.node.internal.json.BlockJson;

/**
 * The implementation of a non-genesis block of the Mokamint blockchain.
 */
@Immutable
public non-sealed class NonGenesisBlockImpl extends AbstractBlock<NonGenesisBlockDescription, NonGenesisBlockImpl> implements NonGenesisBlock {

	/**
	 * The requests inside this block.
	 */
	private final Request[] requests;

	/**
	 * Creates a new non-genesis block with the given description. It adds a signature to the resulting block,
	 * by using the signature algorithm in the prolog of the deadline and the given private key.
	 * 
	 * @param description the description
	 * @param requests the requests in the block
	 * @param stateId the identifier of the state of the application at the end of this block
	 * @param privateKey the private key for signing the block
	 * @throws SignatureException if the signature of the block failed
	 * @throws InvalidKeyException if the private key is invalid
	 */
	public NonGenesisBlockImpl(NonGenesisBlockDescription description, Stream<Request> requests, byte[] stateId, PrivateKey privateKey) throws InvalidKeyException, SignatureException {
		this(description, requests.map(Objects::requireNonNull).toArray(Request[]::new), stateId, privateKey);
	}

	/**
	 * Creates a new non-genesis block with the given description. It adds a signature to the resulting block,
	 * by using the signature algorithm in the prolog of the deadline and the given private key.
	 * 
	 * @param description the description
	 * @param requests the requests in the block
	 * @param stateId the identifier of the state of the application at the end of this block
	 * @param privateKey the private key for signing the block
	 * @throws SignatureException if the signature of the block failed
	 * @throws InvalidKeyException if the private key is invalid
	 */
	private NonGenesisBlockImpl(NonGenesisBlockDescription description, Request[] requests, byte[] stateId, PrivateKey privateKey) throws InvalidKeyException, SignatureException {
		super(description, stateId, privateKey, block -> block.toByteArrayWithoutSignature(requests));
	
		this.requests = requests;
		ensureRequestsAreNotRepeated();
	}

	/**
	 * Creates a non-genesis block from the given JSON representation.
	 * 
	 * @param description the description of the block, already extracted from {@code json}
	 * @param json the JSON representation
	 * @throws InconsistentJsonException if the JSON representation is inconsistent
	 */
	protected NonGenesisBlockImpl(NonGenesisBlockDescription description, BlockJson json) throws InconsistentJsonException {
		super(description, json);

		var reqs = json.getRequests();
		if (reqs == null)
			throw new InconsistentJsonException("requests cannot be null");

		var reqsAsArray = reqs.toArray(String[]::new);

		this.requests = new Request[reqsAsArray.length];
		for (int pos = 0; pos < reqsAsArray.length; pos++) {
			String reqBase64 = reqsAsArray[pos];
			if (reqBase64 == null)
				throw new InconsistentJsonException("requests cannot hold a null element");

			requests[pos] = Requests.of(Base64.fromBase64String(reqBase64, InconsistentJsonException::new));
		}
		
		try {
			ensureRequestsAreNotRepeated();
		}
		catch (IllegalArgumentException e) {
			throw new InconsistentJsonException(e);
		}
	}

	/**
	 * Unmarshals a non-genesis block from the given context. The description of the block has been already unmarshalled.
	 * 
	 * @param description the description of the block
	 * @param context the context
	 * @return the block
	 * @throws IOException if the block cannot be unmarshalled
	 */
	protected NonGenesisBlockImpl(NonGenesisBlockDescription description, UnmarshallingContext context) throws IOException {
		super(description, context);

		this.requests = context.readLengthAndArray(Requests::from, Request[]::new);;

		try {
			ensureRequestsAreNotRepeated();
		}
		catch (IllegalArgumentException e) {
			throw new IOException(e);
		}
	}

	private void ensureRequestsAreNotRepeated() throws IllegalArgumentException {
		var requests = Stream.of(this.requests).sorted().toArray(Request[]::new);
		for (int pos = 0; pos < requests.length - 1; pos++)
			if (requests[pos].equals(requests[pos + 1]))
				throw new IllegalArgumentException("Repeated request");

	}

	@Override
	public byte[] getHashOfPreviousBlock() {
		return getDescription().getHashOfPreviousBlock();
	}

	@Override
	public Stream<Request> getRequests() {
		return Stream.of(requests);
	}

	@Override
	public int getRequestsCount() {
		return requests.length;
	}

	@Override
	public Request getRequest(int progressive) {
		if (progressive < 0 || progressive >= requests.length)
			throw new IndexOutOfBoundsException(progressive);

		return requests[progressive];
	}

	@Override
	public boolean equals(Object other) {
		return other instanceof NonGenesisBlock ongb && super.equals(other)
			&& Arrays.equals(requests, other instanceof NonGenesisBlockImpl ongbi ? ongbi.requests : ongb.getRequests().toArray(Request[]::new)); // optimization
	}

	@Override
	public void into(MarshallingContext context) throws IOException {
		super.into(context);
		context.writeLengthAndArray(requests);
	}

	@Override
	public void intoWithoutConfigurationData(MarshallingContext context) throws IOException {
		super.intoWithoutConfigurationData(context);
		context.writeLengthAndArray(requests);
	}

	@Override
	protected void intoWithoutSignature(MarshallingContext context) throws IOException {
		super.intoWithoutSignature(context);
		context.writeLengthAndArray(requests);
	}

	@Override
	protected void populate(StringBuilder builder) {
		super.populate(builder);
		builder.append("\n");
	
		if (requests.length == 0)
			builder.append("* 0 transactions");
		else if (requests.length == 1)
			builder.append("* 1 transaction:");
		else
			builder.append("* " + requests.length + " transactions:");
	
		int n = 0;
		var hashingForTransactions = getDescription().getHashingForRequests();
	
		for (var transaction: requests)
			builder.append("\n * #" + n++ + ": " + transaction.getHexHash(hashingForTransactions) + " (" + hashingForTransactions + ")");
	}

	@Override
	protected NonGenesisBlockImpl getThis() {
		return this;
	}

	/**
	 * Yields a marshalling of this object into a byte array, without considering its signature.
	 * 
	 * @return the marshalled bytes
	 */
	private byte[] toByteArrayWithoutSignature(Request[] transactions) {
		try (var baos = new ByteArrayOutputStream(); var context = createMarshallingContext(baos)) {
			super.intoWithoutSignature(context);
			context.writeLengthAndArray(transactions);
			context.flush();
			return baos.toByteArray();
		}
		catch (IOException e) {
			// impossible with a ByteArrayOutputStream
			throw new RuntimeException("Unexpected exception", e);
		}
	}
}