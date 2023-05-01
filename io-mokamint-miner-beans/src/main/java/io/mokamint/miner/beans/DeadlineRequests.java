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

package io.mokamint.miner.beans;

import io.hotmoka.websockets.beans.AbstractEncoder;
import io.mokamint.miner.beans.api.DeadlineRequest;
import io.mokamint.miner.beans.internal.DeadlineRequestDecoder;
import io.mokamint.miner.beans.internal.DeadlineRequestImpl;

/**
 * A provider of deadline requests.
 */
public interface DeadlineRequests {

	/**
	 * Yields a deadline request for the given scoop number and data.
	 * 
	 * @param scoopNumber the scoop number
	 * @param data the data
	 * @return the deadline request
	 */
	static DeadlineRequest of(int scoopNumber, byte[] data) {
		return new DeadlineRequestImpl(scoopNumber, data);
	}

	static class Encoder extends AbstractEncoder<DeadlineRequest> {}

    static class Decoder extends DeadlineRequestDecoder {}
}