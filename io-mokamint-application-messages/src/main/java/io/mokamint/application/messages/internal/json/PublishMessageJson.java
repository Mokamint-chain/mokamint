/*
Copyright 2025 Fausto Spoto

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

package io.mokamint.application.messages.internal.json;

import java.security.NoSuchAlgorithmException;

import io.hotmoka.websockets.beans.AbstractRpcMessageJsonRepresentation;
import io.hotmoka.websockets.beans.api.InconsistentJsonException;
import io.mokamint.application.messages.api.PublishMessage;
import io.mokamint.application.messages.internal.PublishMessageImpl;
import io.mokamint.node.Blocks;

/**
 * The JSON representation of an {@link PublishMessage}.
 */
public abstract class PublishMessageJson extends AbstractRpcMessageJsonRepresentation<PublishMessage> {
	private final Blocks.Json block;

	protected PublishMessageJson(PublishMessage message) {
		super(message);

		this.block = new Blocks.Json(message.getBlock());
	}

	public Blocks.Json getBlock() {
		return block;
	}

	@Override
	public PublishMessage unmap() throws InconsistentJsonException, NoSuchAlgorithmException {
		return new PublishMessageImpl(this);
	}

	@Override
	protected String getExpectedType() {
		return PublishMessage.class.getName();
	}
}