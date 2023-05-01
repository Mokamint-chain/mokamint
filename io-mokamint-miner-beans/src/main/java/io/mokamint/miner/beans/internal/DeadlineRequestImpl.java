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

package io.mokamint.miner.beans.internal;

import java.math.BigInteger;

import io.mokamint.miner.beans.api.DeadlineRequest;

/**
 * The implementation of a request to compute a deadline.
 */
public class DeadlineRequestImpl implements DeadlineRequest {
	private final int scoopNumber;
	private final byte[] data;

	/**
	 * Creates the bean.
	 */
	public DeadlineRequestImpl(int scoopNumber, byte[] data) {
		this.scoopNumber = scoopNumber;
		this.data = data;
	}

	@Override
	public int getScoopNumber() {
		return scoopNumber;
	}

	@Override
	public byte[] getData() {
		return data;
	}

	@Override
	public String toString() {
		return "scoopNumber: " + scoopNumber + ", data: " + toHexString(data);
	}

	private static String toHexString(byte[] bytes) {
	    return String.format("%0" + (bytes.length << 1) + "x", new BigInteger(1, bytes));
	}

	static class GsonHelper {
		private int scoopNumber;
		private byte[] data;

        public DeadlineRequestImpl toBean() {
        	return new DeadlineRequestImpl(scoopNumber, data);
        }
    }
}