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

package io.mokamint.nonce.internal;

import java.util.logging.Level;
import java.util.logging.Logger;

import io.hotmoka.websockets.beans.AbstractDecoder;
import io.mokamint.nonce.api.Deadline;
import io.mokamint.nonce.internal.gson.DeadlineGsonHelper;
import jakarta.websocket.DecodeException;

/**
 * A decoder for {@link io.mokamint.nonce.api.Deadline}.
 */
public class DeadlineDecoder extends AbstractDecoder<Deadline> {

	private final static Logger LOGGER = Logger.getLogger(DeadlineDecoder.class.getName());

	public DeadlineDecoder() {
		super(Deadline.class);
	}

	@Override
	public Deadline decode(String s) throws DecodeException {
		try {
			return gson.fromJson(s, DeadlineGsonHelper.class).toBean();
		}
		catch (Exception e) {
			LOGGER.log(Level.WARNING, "could not decode a Deadline", e);
			throw new DecodeException(s, "could not decode a Deadline", e);
		}
	}
}