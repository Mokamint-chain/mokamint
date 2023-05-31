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

package io.mokamint.nonce.internal.gson;

import java.security.NoSuchAlgorithmException;
import java.util.function.Function;
import java.util.function.Supplier;

import io.hotmoka.crypto.HashingAlgorithms;
import io.hotmoka.websockets.beans.BaseEncoder;
import io.mokamint.nonce.DeadlineDescriptions;
import io.mokamint.nonce.api.DeadlineDescription;

public class DeadlineDescriptionEncoder extends BaseEncoder<DeadlineDescription> {

	public DeadlineDescriptionEncoder() {
		super(DeadlineDescription.class);
	}

	@Override
	public Supplier<DeadlineDescription> map(DeadlineDescription description) {
		return new Json(description);
	}

	private static class Json implements Supplier<DeadlineDescription> {
		private int scoopNumber;
		private byte[] data;
		private String hashing;
	
		private Json(DeadlineDescription description) {
			this.scoopNumber = description.getScoopNumber();
			this.data = description.getData();
			this.hashing = description.getHashing().getName();
		}

		@Override
		public DeadlineDescription get() {
			try {
				return DeadlineDescriptions.of(scoopNumber, data, HashingAlgorithms.mk(hashing, Function.identity()));
			}
			catch (NoSuchAlgorithmException e) {
				throw new IllegalArgumentException(e);
			}
		}
	}
}