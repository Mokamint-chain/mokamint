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

import java.time.LocalDateTime;
import java.util.UUID;
import java.util.function.Function;

import io.mokamint.node.api.NodeInfo;
import io.mokamint.node.api.Version;

/**
 * Implementation of the non-consensus information of a Mokamint node.
 */
public class NodeInfoImpl implements NodeInfo {

	/**
	 * The version of the node.
	 */
	private final Version version;

	/**
	 * The UUID of the node.
	 */
	private final UUID uuid;

	/**
	 * The local date and time of the node, in UTC.
	 */
	private final LocalDateTime localDateTimeUTC;

	/**
	 * Yields a new node information object.
	 * 
	 * @param version the version of the node
	 * @param uuid the UUID of the node
	 * @param localDateTimeUTC the local date and time UTC of the node
	 */
	public NodeInfoImpl(Version version, UUID uuid, LocalDateTime localDateTimeUTC) {
		this(version, uuid, localDateTimeUTC, NullPointerException::new, IllegalArgumentException::new);
	}

	/**
	 * Yields a new node information object.
	 * 
	 * @param version the version of the node
	 * @param uuid the UUID of the node
	 * @param localDateTimeUTC the local date and time UTC of the node
	 * @param onNull the generator of the exception to throw if some argument is {@code null}
	 * @param onIllegal the generator of the exception to throw if some argument has an illegal value
	 * @throws ON_NULL if some argument is {@code null}
	 * @throws ON_ILLEGAL if some argument has an illegal value
	 */
	public <ON_NULL extends Exception, ON_ILLEGAL extends Exception> NodeInfoImpl(Version version, UUID uuid, LocalDateTime localDateTimeUTC, Function<String, ON_NULL> onNull, Function<String, ON_ILLEGAL> onIllegal) throws ON_NULL, ON_ILLEGAL {
		if (version == null)
			throw onNull.apply("version cannot be null");

		this.version = version;

		if (uuid == null)
			throw onNull.apply("uuid cannot be null");

		this.uuid = uuid;

		if (localDateTimeUTC == null)
			throw onNull.apply("localDateTimeUTC cannot be null");

		this.localDateTimeUTC = localDateTimeUTC;
	}

	@Override
	public Version getVersion() {
		return version;
	}

	@Override
	public UUID getUUID() {
		return uuid;
	}

	@Override
	public LocalDateTime getLocalDateTimeUTC() {
		return localDateTimeUTC;
	}

	@Override
	public boolean equals(Object other) {
		return other instanceof NodeInfo ni &&
			uuid.equals(ni.getUUID()) && version.equals(ni.getVersion()) &&
			localDateTimeUTC.equals(ni.getLocalDateTimeUTC());
	}

	@Override
	public int hashCode() {
		return uuid.hashCode() ^ localDateTimeUTC.hashCode();
	}

	@Override
	public String toString() {
		return "version: " + version + ", UUID: " + uuid + ", UTC date and time: " + localDateTimeUTC;
	}
}