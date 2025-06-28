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

package io.mokamint.node.api;

import java.io.IOException;
import java.security.InvalidKeyException;
import java.security.SignatureException;

import io.hotmoka.annotations.Immutable;
import io.hotmoka.marshalling.api.Marshallable;
import io.hotmoka.marshalling.api.MarshallingContext;
import io.mokamint.nonce.api.Deadline;

/**
 * A block of the Mokamint blockchain.
 */
@Immutable
public interface Block extends Marshallable, Whisperable {

	/**
	 * Yields the description of this block.
	 * 
	 * @return the description
	 */
	BlockDescription getDescription();

	/**
	 * Yields the signature of this block, computed from its hash by the node
	 * that mined this block. This signature must have been computed with the
	 * private key corresponding to the node's public key, which is inside the prolog
	 * of the deadline for non-genesis blocks or inside the genesis blocks themselves.
	 * 
	 * @return the signature
	 */
	byte[] getSignature();

	/**
	 * Yields the identifier of the state of the application at the end of the execution of the
	 * transactions from the beginning of the blockchain to the end of this block.
	 * 
	 * @return the identifier of the state of the application
	 */
	byte[] getStateId();

	/**
	 * Yields the hash of this block. This hash does not use the signature of the node
	 * (if any) which is, instead, computed from this hash and the private key of the signer.
	 * 
	 * @return the hash of this block
	 */
	byte[] getHash();

	/**
	 * Yields the hash of this block, as a hexadecimal string.
	 * 
	 * @return the hash of this block, as a hexadecimal string
	 */
	String getHexHash();

	/**
	 * Determines if the signature of this block is valid, that is,
	 * it is a correct signature of this block with the key in the description of the block.
	 * 
	 * @return true if and only if the signature of this block is valid
	 * @throws InvalidKeyException if the key in the description of this block is invalid
	 * @throws SignatureException if the signature in this block cannot be verified
	 */
	boolean signatureIsValid() throws InvalidKeyException, SignatureException;

	/**
	 * Yields the description of the next block, assuming that it has the given deadline.
	 * 
	 * @param deadline the deadline of the next block
	 * @return the description
	 */
	NonGenesisBlockDescription getNextBlockDescription(Deadline deadline);

	@Override
	String toString();

	/**
	 * Marshals this object into a given stream. This method in general
	 * performs better than standard Java serialization, wrt the size of the marshalled data.
	 * It does not report information that can be recomputed from the configuration of the
	 * node storing this block.
	 * 
	 * @param context the context holding the stream
	 * @throws IOException if this object cannot be marshalled
	 */
	void intoWithoutConfigurationData(MarshallingContext context) throws IOException;

	/**
	 * Yields the serialization of this object into bytes. It does not report information that can be recomputed
	 * from the configuration of the node storing this block.
	 * 
	 * @return the serialization into bytes of this object
	 */
	byte[] toByteArrayWithoutConfigurationData();
}