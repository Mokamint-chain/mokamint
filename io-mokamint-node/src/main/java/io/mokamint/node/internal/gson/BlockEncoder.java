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
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.function.Supplier;

import io.hotmoka.websockets.beans.BaseEncoder;
import io.mokamint.node.Blocks;
import io.mokamint.node.api.Block;
import io.mokamint.node.api.GenesisBlock;
import io.mokamint.node.api.NonGenesisBlock;
import io.mokamint.nonce.Deadlines;
import io.mokamint.nonce.api.Deadline;

public class BlockEncoder extends BaseEncoder<Block> {

	public BlockEncoder() {
		super(Block.class);
	}

	@Override
	public Supplier<Block> map(Block block) {
		if (block instanceof GenesisBlock)
			return new GenesisJson((GenesisBlock) block);
		else
			return new NonGenesisJson((NonGenesisBlock) block);
	}

	private static class GenesisJson implements Supplier<Block> {
		private String startDateTimeUTC;

		private GenesisJson(GenesisBlock block) {
			this.startDateTimeUTC = DateTimeFormatter.ISO_LOCAL_DATE_TIME.format(block.getStartDateTimeUTC());
		}

		@Override
		public Block get() {
			return Blocks.genesis(LocalDateTime.parse(startDateTimeUTC, DateTimeFormatter.ISO_LOCAL_DATE_TIME));
		}
	}

	private static class NonGenesisJson implements Supplier<Block> {
		private long height;
		private long totalWaitingTime;
		private long weightedWaitingTime;
		private BigInteger acceleration;
		@SuppressWarnings("rawtypes")
		private Supplier deadline;
		private byte[] hashOfPreviousBlock;

		private NonGenesisJson(NonGenesisBlock block) {
			this.height = block.getHeight();
			this.totalWaitingTime = block.getTotalWaitingTime();
			this.weightedWaitingTime = block.getWeightedWaitingTime();
			this.acceleration = block.getAcceleration();
			this.deadline = new Deadlines.Encoder().map(block.getDeadline());
			this.hashOfPreviousBlock = block.getHashOfPreviousBlock();
		}

		@Override
		public Block get() {
			return Blocks.of(height, totalWaitingTime, weightedWaitingTime, acceleration, (Deadline) deadline.get(), hashOfPreviousBlock);
		}
	}
}