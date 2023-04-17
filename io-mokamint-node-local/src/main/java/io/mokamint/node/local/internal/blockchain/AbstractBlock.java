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

package io.mokamint.node.local.internal.blockchain;

import java.math.BigInteger;

import io.hotmoka.crypto.api.HashingAlgorithm;
import io.mokamint.nonce.api.Nonce;

/**
 * Shared code of blocks.
 */
public abstract class AbstractBlock implements Block {

	private final static BigInteger SCOOPS_PER_NONCE = BigInteger.valueOf(Nonce.SCOOPS_PER_NONCE);

	@Override
	public int getNewScoopNumber(HashingAlgorithm<byte[]> hashing) {
		byte[] generationHash = hashing.hash(concat(getNewGenerationSignature(hashing), longToBytesBE(getHeight() + 1)));
		return new BigInteger(1, generationHash).remainder(SCOOPS_PER_NONCE).intValue();
	}

	protected static byte[] concat(byte[] array1, byte[] array2) {
		byte[] merge = new byte[array1.length + array2.length];
		System.arraycopy(array1, 0, merge, 0, array1.length);
		System.arraycopy(array2, 0, merge, array1.length, array2.length);
		return merge;
	}

	private static byte[] longToBytesBE(long l) {
		byte[] target = new byte[8];

		for (int i = 0; i <= 7; i++)
			target[7 - i] = (byte) ((l>>(8*i)) & 0xFF);

		return target;
	}
}