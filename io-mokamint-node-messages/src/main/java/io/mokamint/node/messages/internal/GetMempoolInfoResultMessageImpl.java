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

package io.mokamint.node.messages.internal;

import java.util.Objects;

import io.hotmoka.websockets.beans.AbstractRpcMessage;
import io.mokamint.node.api.MempoolInfo;
import io.mokamint.node.api.PublicNode;
import io.mokamint.node.messages.api.GetMempoolInfoResultMessage;

/**
 * Implementation of the network message corresponding to the result of the {@link PublicNode#getMempoolInfo()} method.
 */
public class GetMempoolInfoResultMessageImpl extends AbstractRpcMessage implements GetMempoolInfoResultMessage {

	private final MempoolInfo info;

	/**
	 * Creates the message.
	 * 
	 * @param info the mempool information in the message
	 * @param id the identifier of the message
	 */
	public GetMempoolInfoResultMessageImpl(MempoolInfo info, String id) {
		super(id);

		this.info = Objects.requireNonNull(info, "info cannot be null");
	}

	@Override
	public MempoolInfo get() {
		return info;
	}

	@Override
	public boolean equals(Object other) {
		return other instanceof GetMempoolInfoResultMessage gmprm && super.equals(other) && info.equals(gmprm.get());
	}

	@Override
	protected String getExpectedType() {
		return GetMempoolInfoResultMessage.class.getName();
	}
}