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
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Optional;
import java.util.stream.Stream;

import io.hotmoka.annotations.Immutable;
import io.hotmoka.crypto.Hex;
import io.hotmoka.crypto.api.Hasher;
import io.hotmoka.marshalling.AbstractMarshallingContext;
import io.hotmoka.marshalling.api.MarshallingContext;
import io.hotmoka.marshalling.api.UnmarshallingContext;
import io.mokamint.node.Transactions;
import io.mokamint.node.api.NonGenesisBlock;
import io.mokamint.node.api.NonGenesisBlockDescription;
import io.mokamint.node.api.Transaction;
import io.mokamint.nonce.api.Deadline;

/**
 * The implementation of a non-genesis block of the Mokamint blockchain.
 */
@Immutable
public non-sealed class NonGenesisBlockImpl extends AbstractBlock<NonGenesisBlockDescription> implements NonGenesisBlock {

	/**
	 * The transactions inside this block.
	 */
	private final Transaction[] transactions;

	/**
	 * Creates a new non-genesis block with the given description. It adds a signature to the resulting block,
	 * by using the signature algorithm in the prolog of the deadline and the given private key.
	 * 
	 * @param description the description
	 * @param transactions the transactions in the block
	 * @param stateId the identifier of the state of the application at the end of this block
	 * @param privateKey the private key for signing the block
	 * @throws SignatureException if the signature of the block failed
	 * @throws InvalidKeyException if the private key is invalid
	 */
	public NonGenesisBlockImpl(NonGenesisBlockDescription description, Stream<Transaction> transactions, byte[] stateId, PrivateKey privateKey) throws InvalidKeyException, SignatureException {
		this(description, transactions.toArray(Transaction[]::new), stateId, privateKey);
	}

	/**
	 * Creates a new non-genesis block with the given description and signature.
	 * 
	 * @param description the description
	 * @param transactions the transactions in the block
	 * @param stateId the identifier of the state of the application at the end of this block
	 * @param signature the signature that will be put in the block
	 * @throws SignatureException if the signature of this block cannot be verified or the signature is invalid
	 * @throws InvalidKeyException if the public key of the description is invalid
	 */
	public NonGenesisBlockImpl(NonGenesisBlockDescription description, Stream<Transaction> transactions, byte[] stateId, byte[] signature) throws InvalidKeyException, SignatureException {
		super(description, stateId, signature);

		this.transactions = transactions.toArray(Transaction[]::new);
		verify(toByteArrayWithoutSignature(description, stateId, this.transactions));	
	}

	/**
	 * Unmarshals a non-genesis block from the given context. The description of the block has been already read.
	 * 
	 * @param description the description of the block
	 * @param context the context
	 * @return the block
	 * @throws IOException if the block cannot be unmarshalled
	 */
	NonGenesisBlockImpl(NonGenesisBlockDescription description, UnmarshallingContext context) throws IOException {
		super(description, context);

		try {
			this.transactions = context.readLengthAndArray(Transactions::from, Transaction[]::new);
			verify(toByteArrayWithoutSignature(description, getStateId(), transactions));
		}
		catch (RuntimeException | InvalidKeyException | SignatureException e) {
			throw new IOException(e);
		}
	}

	/**
	 * Creates a new non-genesis block with the given description. It adds a signature to the resulting block,
	 * by using the signature algorithm in the prolog of the deadline and the given private key.
	 * 
	 * @param description the description
	 * @param transactions the transactions in the block
	 * @param stateId the identifier of the state of the application at the end of this block
	 * @param privateKey the private key for signing the block
	 * @throws SignatureException if the signature of the block failed
	 * @throws InvalidKeyException if the private key is invalid
	 */
	private NonGenesisBlockImpl(NonGenesisBlockDescription description, Transaction[] transactions, byte[] stateId, PrivateKey privateKey) throws InvalidKeyException, SignatureException {
		super(description, stateId, privateKey, toByteArrayWithoutSignature(description, stateId, transactions));
	
		this.transactions = transactions;
		verify(toByteArrayWithoutSignature(description, stateId, transactions));
	}

	/**
	 * Yields a marshalling of this object into a byte array, without considering its signature.
	 * 
	 * @return the marshalled bytes
	 */
	private static byte[] toByteArrayWithoutSignature(NonGenesisBlockDescription description, byte[] stateId, Transaction[] transactions) {
		try (var baos = new ByteArrayOutputStream(); var context = new AbstractMarshallingContext(baos) {}) {
			description.into(context);
			context.writeLengthAndBytes(stateId);
			context.writeLengthAndArray(transactions);
			context.flush();
			return baos.toByteArray();
		}
		catch (IOException e) {
			// impossible with a ByteArrayOutputStream
			throw new RuntimeException("Unexpected exception", e);
		}
	}

	@Override
	public Deadline getDeadline() {
		return getDescription().getDeadline();
	}

	@Override
	public byte[] getHashOfPreviousBlock() {
		return getDescription().getHashOfPreviousBlock();
	}

	@Override
	public Stream<Transaction> getTransactions() {
		return Stream.of(transactions);
	}

	@Override
	public int getTransactionsCount() {
		return transactions.length;
	}

	@Override
	public Transaction getTransaction(int progressive) {
		if (progressive < 0 || progressive >= transactions.length)
			throw new IndexOutOfBoundsException(progressive);

		return transactions[progressive];
	}

	@Override
	public boolean equals(Object other) {
		if (other instanceof NonGenesisBlock ongb && super.equals(other))
			if (other instanceof NonGenesisBlockImpl ongbi)
				return Arrays.equals(transactions, ongbi.transactions); // optimization
			else
				return Arrays.equals(transactions, ongb.getTransactions().toArray(Transaction[]::new));
		else
			return false;
	}

	@Override
	public void into(MarshallingContext context) throws IOException {
		super.into(context);
		context.writeLengthAndArray(transactions);
	}

	@Override
	public void intoWithoutConfigurationData(MarshallingContext context) throws IOException {
		super.intoWithoutConfigurationData(context);
		context.writeLengthAndArray(transactions);
	}

	@Override
	public final String toString(Optional<LocalDateTime> startDateTimeUTC) {
		var builder = new StringBuilder(super.toString(startDateTimeUTC));
		builder.append("\n");

		if (transactions.length == 0)
			builder.append("* 0 transactions");
		else if (transactions.length == 1)
			builder.append("* 1 transaction:");
		else
			builder.append("* " + transactions.length + " transactions:");

		int n = 0;
		var hashingForTransactions = getDescription().getHashingForTransactions();
		Hasher<Transaction> hasher = hashingForTransactions.getHasher(Transaction::toByteArray);

		for (var transaction: transactions)
			builder.append("\n * #" + n++ + ": " + Hex.toHexString(hasher.hash(transaction)) + " (" + hashingForTransactions + ")");

		return builder.toString();
	}

	@Override
	protected void verify(byte[] bytesToSign) throws InvalidKeyException, SignatureException {
		super.verify(bytesToSign);
	
		var transactions = Stream.of(this.transactions).sorted().toArray(Transaction[]::new);
		for (int pos = 0; pos < transactions.length - 1; pos++)
			if (transactions[pos].equals(transactions[pos + 1]))
				throw new IllegalArgumentException("Repeated transaction");
	}
}