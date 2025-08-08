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

package io.mokamint.node.internal.json;

import static java.time.format.DateTimeFormatter.ISO_LOCAL_DATE_TIME;

import io.hotmoka.websockets.beans.api.InconsistentJsonException;
import io.hotmoka.websockets.beans.api.JsonRepresentation;
import io.mokamint.node.Versions;
import io.mokamint.node.api.NodeInfo;
import io.mokamint.node.internal.NodeInfoImpl;

/**
 * The JSON representation of a {@link NodeInfo}.
 */
public abstract class NodeInfoJson implements JsonRepresentation<NodeInfo> {
	private final Versions.Json version;
	private final String uuid;
	private final String localDateTimeUTC;
	private final int maxChainPortionLength;
	private final int maxMempoolPortionLength;

	protected NodeInfoJson(NodeInfo info) {
		this.version = new Versions.Json(info.getVersion());
		this.uuid = info.getUUID().toString();
		this.localDateTimeUTC = ISO_LOCAL_DATE_TIME.format(info.getLocalDateTimeUTC());
		this.maxChainPortionLength = info.getMaxChainPortionLength();
		this.maxMempoolPortionLength = info.getMaxMempoolPortionLength();
	}

	public Versions.Json getVersion() {
		return version;
	}

	public String getUuid() {
		return uuid;
	}

	public String getLocalDateTimeUTC() {
		return localDateTimeUTC;
	}

	public int getMaxChainPortionLength() {
		return maxChainPortionLength;
	}

	public int getMaxMempoolPortionLength() {
		return maxMempoolPortionLength;
	}

	@Override
	public NodeInfo unmap() throws InconsistentJsonException {
		return new NodeInfoImpl(this);
	}
}