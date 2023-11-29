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
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.spec.InvalidKeySpecException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

import io.hotmoka.crypto.Base58;
import io.hotmoka.crypto.Base58ConversionException;
import io.hotmoka.crypto.Hex;
import io.hotmoka.crypto.HexConversionException;
import io.hotmoka.crypto.SignatureAlgorithms;
import io.hotmoka.websockets.beans.api.JsonRepresentation;
import io.mokamint.node.BlockDescriptions;
import io.mokamint.node.api.BlockDescription;
import io.mokamint.node.api.GenesisBlockDescription;
import io.mokamint.node.api.NonGenesisBlockDescription;
import io.mokamint.nonce.Deadlines;

/**
 * The JSON representation of a {@link BlockDescription}.
 */
public abstract class BlockDescriptionJson implements JsonRepresentation<BlockDescription> {
	private String startDateTimeUTC;
	private Long height;
	private BigInteger power;
	private Long totalWaitingTime;
	private Long weightedWaitingTime;
	private final BigInteger acceleration;
	private Deadlines.Json deadline;
	private String hashOfPreviousBlock;
	private String signatureForBlocks;
	private String publicKey;

	protected BlockDescriptionJson(BlockDescription description) throws InvalidKeyException {
		if (description instanceof GenesisBlockDescription gbd) {
			this.startDateTimeUTC = DateTimeFormatter.ISO_LOCAL_DATE_TIME.format(gbd.getStartDateTimeUTC());
			this.acceleration = gbd.getAcceleration();
			var signature = gbd.getSignatureForBlock();
			this.signatureForBlocks = signature.getName();
			this.publicKey = Base58.encode(signature.encodingOf(gbd.getPublicKeyForSigningBlock()));
		}
		else {
			var ngbd = (NonGenesisBlockDescription) description;
			this.height = ngbd.getHeight();
			this.power = ngbd.getPower();
			this.totalWaitingTime = ngbd.getTotalWaitingTime();
			this.weightedWaitingTime = ngbd.getWeightedWaitingTime();
			this.acceleration = ngbd.getAcceleration();
			this.deadline = new Deadlines.Json(ngbd.getDeadline());
			this.hashOfPreviousBlock = Hex.toHexString(ngbd.getHashOfPreviousBlock());
		}
	}

	@Override
	public BlockDescription unmap() throws NoSuchAlgorithmException, InvalidKeySpecException, HexConversionException, InvalidKeyException, Base58ConversionException {
		if (startDateTimeUTC == null)
			return BlockDescriptions.of(height, power, totalWaitingTime, weightedWaitingTime, acceleration, deadline.unmap(), Hex.fromHexString(hashOfPreviousBlock));
		else {
			var signature = SignatureAlgorithms.of(signatureForBlocks);

			return BlockDescriptions.genesis(LocalDateTime.parse(startDateTimeUTC, DateTimeFormatter.ISO_LOCAL_DATE_TIME),
				acceleration, signature, signature.publicKeyFromEncoding(Base58.decode(publicKey)));
		}
	}
}