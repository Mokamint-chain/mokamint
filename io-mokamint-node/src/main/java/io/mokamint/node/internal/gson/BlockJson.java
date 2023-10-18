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

package io.mokamint.node.internal.gson;

import java.math.BigInteger;
import java.security.NoSuchAlgorithmException;
import java.security.spec.InvalidKeySpecException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

import io.hotmoka.crypto.Hex;
import io.hotmoka.crypto.HexConversionException;
import io.hotmoka.crypto.SignatureAlgorithms;
import io.hotmoka.websockets.beans.api.JsonRepresentation;
import io.mokamint.node.Blocks;
import io.mokamint.node.api.Block;
import io.mokamint.node.api.GenesisBlock;
import io.mokamint.node.api.NonGenesisBlock;
import io.mokamint.nonce.Deadlines;

/**
 * The JSON representation of a {@link Block}.
 */
public abstract class BlockJson implements JsonRepresentation<Block> {
	private String startDateTimeUTC;
	private Long height;
	private BigInteger power;
	private Long totalWaitingTime;
	private Long weightedWaitingTime;
	private BigInteger acceleration;
	private Deadlines.Json deadline;
	private String hashOfPreviousBlock;
	private String signatureForBlocks;
	private String publicKeyBase58;
	private String signature;

	protected BlockJson(Block block) {
		if (block instanceof GenesisBlock gb) {
			this.startDateTimeUTC = DateTimeFormatter.ISO_LOCAL_DATE_TIME.format(gb.getStartDateTimeUTC());
			this.acceleration = gb.getAcceleration();
			this.signature = Hex.toHexString(gb.getSignature());
			this.signatureForBlocks = gb.getSignatureForBlocks().getName();
			this.publicKeyBase58 = gb.getPublicKeyForSigningThisBlockBase58();
		}
		else {
			var ngb = (NonGenesisBlock) block;
			this.height = ngb.getHeight();
			this.power = ngb.getPower();
			this.totalWaitingTime = ngb.getTotalWaitingTime();
			this.weightedWaitingTime = ngb.getWeightedWaitingTime();
			this.acceleration = ngb.getAcceleration();
			this.deadline = new Deadlines.Encoder().map(ngb.getDeadline());
			this.hashOfPreviousBlock = Hex.toHexString(ngb.getHashOfPreviousBlock());
			this.signature = Hex.toHexString(ngb.getSignature());
		}
	}

	@Override
	public Block unmap() throws NoSuchAlgorithmException, InvalidKeySpecException, HexConversionException {
		if (startDateTimeUTC == null)
			return Blocks.of(height, power, totalWaitingTime, weightedWaitingTime, acceleration, deadline.unmap(),
				Hex.fromHexString(hashOfPreviousBlock), Hex.fromHexString(signature));
		else
			return Blocks.genesis(LocalDateTime.parse(startDateTimeUTC, DateTimeFormatter.ISO_LOCAL_DATE_TIME),
				acceleration, SignatureAlgorithms.of(signatureForBlocks), publicKeyBase58, Hex.fromHexString(signature));
	}
}