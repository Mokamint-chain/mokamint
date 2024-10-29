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

import static java.time.format.DateTimeFormatter.ISO_LOCAL_DATE_TIME;

import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.util.UUID;

import io.hotmoka.websockets.beans.api.InconsistentJsonException;
import io.hotmoka.websockets.beans.api.JsonRepresentation;
import io.mokamint.node.NodeInfos;
import io.mokamint.node.Versions;
import io.mokamint.node.api.NodeInfo;

/**
 * The JSON representation of a {@link NodeInfo}.
 */
public abstract class NodeInfoJson implements JsonRepresentation<NodeInfo> {
	private final Versions.Json version;
	private final String uuid;
	private final String localDateTimeUTC;

	protected NodeInfoJson(NodeInfo info) {
		this.version = new Versions.Json(info.getVersion());
		this.uuid = info.getUUID().toString();
		this.localDateTimeUTC = ISO_LOCAL_DATE_TIME.format(info.getLocalDateTimeUTC());
	}

	@Override
	public NodeInfo unmap() throws InconsistentJsonException {
		try {
			return NodeInfos.of(version.unmap(), UUID.fromString(uuid), LocalDateTime.parse(localDateTimeUTC, ISO_LOCAL_DATE_TIME));
		}
		catch (DateTimeParseException | IllegalArgumentException e) {
			throw new InconsistentJsonException(e);
		}
	}
}