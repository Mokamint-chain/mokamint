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

import io.hotmoka.websockets.beans.AbstractDecoder;
import io.mokamint.miner.beans.api.DeadlineRequest;
import jakarta.websocket.DecodeException;

/**
 * A decoder for {@link io.mokamint.miner.beans.api.DeadlineRequest}.
 */
public class DeadlineRequestDecoder extends AbstractDecoder<DeadlineRequest> {

	public DeadlineRequestDecoder() {
		super(DeadlineRequest.class);
	}

	@Override
	public DeadlineRequest decode(String s) throws DecodeException {
		try {
			return gson.fromJson(s, DeadlineRequestImpl.GsonHelper.class).toBean();
		}
		catch (Exception e) {
			throw new DecodeException(s, "could not decode a DeadlineRequest", e);
		}
	}
}