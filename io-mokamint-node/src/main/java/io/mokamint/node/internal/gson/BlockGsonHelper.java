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

import io.mokamint.node.api.Block;
import io.mokamint.node.internal.GenesisBlockImpl;
import io.mokamint.node.internal.NonGenesisBlockImpl;
import io.mokamint.nonce.api.Deadline;

/**
 * An object that can be encoded/decoded with Gson. It corresponds to a {@link Block}.
 */
public class BlockGsonHelper {
	private String startDateTimeUTC;
	private long height;
	private long totalWaitingTime;
	private long weightedWaitingTime;
	private BigInteger acceleration;
	private Deadline deadline;
	private byte[] hashOfPreviousBlock;

	public Block toBean() {
		if (startDateTimeUTC != null)
			return new GenesisBlockImpl(LocalDateTime.parse(startDateTimeUTC, DateTimeFormatter.ISO_LOCAL_DATE_TIME));
		else
			return new NonGenesisBlockImpl(height, totalWaitingTime, weightedWaitingTime, acceleration, deadline, hashOfPreviousBlock);
    }
}