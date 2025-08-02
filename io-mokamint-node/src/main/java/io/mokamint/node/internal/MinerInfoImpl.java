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

package io.mokamint.node.internal;

import java.util.Objects;
import java.util.UUID;

import io.hotmoka.annotations.Immutable;
import io.hotmoka.websockets.beans.api.InconsistentJsonException;
import io.mokamint.node.api.MinerInfo;
import io.mokamint.node.internal.json.MinerInfoJson;

/**
 * An implementation of miner information.
 */
@Immutable
public class MinerInfoImpl implements MinerInfo {
	private final UUID uuid;
	private final long points;
	private final String description;

	/**
	 * Yields a miner information object.
	 * 
	 * @param uuid the unique identifier of the miner
	 * @param points the points of the miner
	 * @param description the description of the miner
	 * @return the miner information object
	 */
	public MinerInfoImpl(UUID uuid, long points, String description) {
		this.uuid = Objects.requireNonNull(uuid);
		this.description = Objects.requireNonNull(description);

		if (points <= 0)
			throw new IllegalArgumentException("points must be positive");

		this.points = points;
	}

	/**
	 * Creates a miner info from the given JSON representation.
	 * 
	 * @param json the JSON representation
	 * @throws InconsistentJsonException if the JSON representation is inconsistent
	 */
	public MinerInfoImpl(MinerInfoJson json) throws InconsistentJsonException {
		String uuid = json.getUuid();
		if (uuid == null)
			throw new InconsistentJsonException("uuid cannot be null");

		try {
			this.uuid = UUID.fromString(uuid);
		}
		catch (IllegalArgumentException e) {
			throw new InconsistentJsonException(e);
		}

		String description = json.getDescription();
		if (description == null)
			throw new InconsistentJsonException("description cannot be null");

		this.description = description;

		long points = json.getPoints();
		if (points <= 0)
			throw new InconsistentJsonException("points must be positive");

		this.points = points;
	}

	@Override
	public UUID getUUID() {
		return uuid;
	}

	@Override
	public long getPoints() {
		return points;
	}

	@Override
	public String getDescription() {
		return description;
	}

	@Override
	public boolean equals(Object other) {
		return other instanceof MinerInfo info &&
			uuid.equals(info.getUUID()) &&
			points == info.getPoints() &&
			description.equals(info.getDescription());
	}

	@Override
	public int hashCode() {
		return uuid.hashCode();
	}

	@Override
	public int compareTo(MinerInfo other) {
		int diff = -Long.compare(points, other.getPoints());
		if (diff != 0)
			return diff;
		else
			return uuid.compareTo(other.getUUID());
	}

	@Override
	public String toString() {
		return uuid + ": " + description + ", points = " + points;
	}
}