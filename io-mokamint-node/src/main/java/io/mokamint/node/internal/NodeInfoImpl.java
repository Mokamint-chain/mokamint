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

import static java.time.format.DateTimeFormatter.ISO_LOCAL_DATE_TIME;

import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.util.Objects;
import java.util.UUID;

import io.hotmoka.websockets.beans.api.InconsistentJsonException;
import io.mokamint.node.Versions;
import io.mokamint.node.api.NodeInfo;
import io.mokamint.node.api.Version;
import io.mokamint.node.internal.json.NodeInfoJson;

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
		this.version = Objects.requireNonNull(version);
		this.uuid = Objects.requireNonNull(uuid);
		this.localDateTimeUTC = Objects.requireNonNull(localDateTimeUTC);
	}

	/**
	 * Creates a node info from the given JSON representation.
	 * 
	 * @param json the JSON representation
	 * @throws InconsistentJsonException if the JSON representation is inconsistent
	 */
	public NodeInfoImpl(NodeInfoJson json) throws InconsistentJsonException {
		Versions.Json version = json.getVersion();
		if (version == null)
			throw new InconsistentJsonException("version cannot be null");

		this.version = version.unmap();

		String uuid = json.getUuid();
		if (uuid == null)
			throw new InconsistentJsonException("uuid cannot be null");

		try {
			this.uuid = UUID.fromString(uuid);
		}
		catch (IllegalArgumentException e) {
			throw new InconsistentJsonException(e);
		}

		var localDateTimeUTC = json.getLocalDateTimeUTC();
		if (localDateTimeUTC == null)
			throw new InconsistentJsonException("localDateTimeUTC cannot be null");

		try {
			this.localDateTimeUTC = LocalDateTime.parse(localDateTimeUTC, ISO_LOCAL_DATE_TIME);
		}
		catch (DateTimeParseException e) {
			throw new InconsistentJsonException(e);
		}
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