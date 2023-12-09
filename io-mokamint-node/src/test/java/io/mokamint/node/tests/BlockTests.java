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

package io.mokamint.node.tests;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.math.BigInteger;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.SignatureException;
import java.time.LocalDateTime;
import java.util.stream.Stream;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import io.hotmoka.crypto.HashingAlgorithms;
import io.hotmoka.crypto.SignatureAlgorithms;
import io.hotmoka.testing.AbstractLoggedTests;
import io.mokamint.node.BlockDescriptions;
import io.mokamint.node.Blocks;
import io.mokamint.node.Transactions;
import io.mokamint.nonce.Deadlines;
import io.mokamint.nonce.Prologs;
import jakarta.websocket.DecodeException;
import jakarta.websocket.EncodeException;

public class BlockTests extends AbstractLoggedTests {

	@Test
	@DisplayName("genesis blocks are correctly encoded into Json and decoded from Json")
	public void encodeDecodeWorksForGenesis() throws EncodeException, DecodeException, NoSuchAlgorithmException, InvalidKeyException, SignatureException {
		var ed25519 = SignatureAlgorithms.ed25519();
		var keys = ed25519.getKeyPair();
		var transaction1 = Transactions.of(new byte[] { 13, 17, 23, 31 });
		var transaction2 = Transactions.of(new byte[] { 5, 6, 7 });
		var transaction3 = Transactions.of(new byte[] {});
		var block1 = Blocks.genesis(BlockDescriptions.genesis(LocalDateTime.now(), BigInteger.ONE, ed25519, keys.getPublic()), Stream.of(transaction1, transaction2, transaction3), new byte[0], keys.getPrivate());
		String encoded = new Blocks.Encoder().encode(block1);
		var block2 = new Blocks.Decoder().decode(encoded);
		assertEquals(block1, block2);
	}

	@Test
	@DisplayName("non-genesis blocks are correctly encoded into Json and decoded from Json")
	public void encodeDecodeWorksForNonGenesis() throws EncodeException, DecodeException, NoSuchAlgorithmException, InvalidKeyException, SignatureException {
		var hashing = HashingAlgorithms.shabal256();
		var value = new byte[hashing.length()];
		for (int pos = 0; pos < value.length; pos++)
			value[pos] = (byte) pos;
		var ed25519 = SignatureAlgorithms.ed25519();
		var nodeKeyPair = ed25519.getKeyPair();
		var plotKeyPair = ed25519.getKeyPair();
		var prolog = Prologs.of("octopus", ed25519, nodeKeyPair.getPublic(), ed25519, plotKeyPair.getPublic(), new byte[0]);
		var deadline = Deadlines.of(prolog, 13, value, 11, new byte[] { 90, 91, 92 }, hashing, plotKeyPair.getPrivate());
		var transaction1 = Transactions.of(new byte[] { 13, 17, 23, 31 });
		var transaction2 = Transactions.of(new byte[] { 5, 6, 7 });
		var transaction3 = Transactions.of(new byte[] {});
		var block1 = Blocks.of(BlockDescriptions.of(13, BigInteger.TEN, 1234L, 1100L, BigInteger.valueOf(13011973), deadline, new byte[] { 1, 2, 3, 4, 5, 6}), Stream.of(transaction1, transaction2, transaction3), new byte[0], nodeKeyPair.getPrivate());
		String encoded = new Blocks.Encoder().encode(block1);
		var block2 = new Blocks.Decoder().decode(encoded);
		assertEquals(block1, block2);
	}
}