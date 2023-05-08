/*
Copyright 2021 Fausto Spoto

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

package io.mokamint.nonce.internal;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import io.hotmoka.crypto.api.HashingAlgorithm;
import io.hotmoka.websockets.beans.AbstractEncoder;
import io.mokamint.nonce.api.Deadline;

public class DeadlineEncoder extends AbstractEncoder<Deadline> {

	public DeadlineEncoder() {
		super(createGson());
	}

	private static Gson createGson() {
		GsonBuilder gson = new GsonBuilder();
		gson.registerTypeAdapter(HashingAlgorithm.class, new HashingAlgorithmSerializer());
		return gson.create();
	}
}