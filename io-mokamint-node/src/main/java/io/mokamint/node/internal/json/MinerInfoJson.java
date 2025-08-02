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

import io.hotmoka.websockets.beans.api.InconsistentJsonException;
import io.hotmoka.websockets.beans.api.JsonRepresentation;
import io.mokamint.node.api.MinerInfo;
import io.mokamint.node.internal.MinerInfoImpl;

/**
 * The JSON representation of a {@link MinerInfo}.
 */
public abstract class MinerInfoJson implements JsonRepresentation<MinerInfo> {
	private final String uuid;
	private final long points;
	private final String description;

	protected MinerInfoJson(MinerInfo info) {
		this.uuid = info.getUUID().toString();
		this.points = info.getPoints();
		this.description = info.getDescription();
	}

	public String getUuid() {
		return uuid;
	}

	public long getPoints() {
		return points;
	}

	public String getDescription() {
		return description;
	}

	@Override
	public MinerInfo unmap() throws InconsistentJsonException {
		return new MinerInfoImpl(this);
	}
}