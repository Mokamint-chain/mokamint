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

import java.util.UUID;
import java.util.function.Function;

import io.hotmoka.annotations.Immutable;
import io.mokamint.node.api.MinerInfo;

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
		this(uuid, points, description, NullPointerException::new, IllegalArgumentException::new);
	}

	/**
	 * Creates a miner information object.
	 * 
	 * @param uuid the unique identifier of the miner
	 * @param points the points of the miner
	 * @param description the description of the miner
	 * @param onNull the generator of the exception to throw if some argument is {@code null}
	 * @param onIllegal the generator of the exception to throw if some argument has an illegal value
	 * @throws ON_NULL if some argument is {@code null}
	 * @throws ON_ILLEGAL if some argument has an illegal value
	 */
	public <ON_NULL extends Exception, ON_ILLEGAL extends Exception> MinerInfoImpl(UUID uuid, long points, String description, Function<String, ON_NULL> onNull, Function<String, ON_ILLEGAL> onIllegal) throws ON_NULL, ON_ILLEGAL {
		if (uuid == null)
			throw onNull.apply("uuid cannot be null");

		this.uuid = uuid;

		if (description == null)
			throw onNull.apply("description cannot be null");

		this.description = description;

		if (points <= 0)
			throw onIllegal.apply("points must be positive");

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