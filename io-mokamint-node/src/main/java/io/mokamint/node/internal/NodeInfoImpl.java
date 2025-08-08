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
import java.util.UUID;

import io.hotmoka.exceptions.ExceptionSupplierFromMessage;
import io.hotmoka.exceptions.Objects;
import io.hotmoka.websockets.beans.api.InconsistentJsonException;
import io.mokamint.node.api.NodeInfo;
import io.mokamint.node.api.PublicNode;
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
	 * The maximal number of block hashes that can be fetched with a single
	 * {@link PublicNode#getChainPortion(long, int)} call.
	 */
	private final int maxChainPortionLength;

	/**
	 * The maximal number of mempool elements that can be fetched with a single
	 * {@link PublicNode#getMempoolPortion(int, int)} call.
	 */
	private final int maxMempoolPortionLength;

	/**
	 * Yields a new node information object.
	 * 
	 * @param version the version of the node
	 * @param uuid the UUID of the node
	 * @param localDateTimeUTC the local date and time UTC of the node
	 * @param maxChainPortionLength the maximal number of block hashes that can be fetched with a single
	 *                              {@link PublicNode#getChainPortion(long, int)} call
	 * @param maxMempoolPortionLength the maximal number of mempool elements that can be fetched with a single
	 *                                {@link PublicNode#getMempoolPortion(int, int)} call
	 */
	public NodeInfoImpl(Version version, UUID uuid, LocalDateTime localDateTimeUTC, int maxChainPortionLength, int maxMempoolPortionLength) {
		this(version, uuid, localDateTimeUTC, maxChainPortionLength, maxMempoolPortionLength, IllegalArgumentException::new);
	}

	/**
	 * Creates a node info from the given JSON representation.
	 * 
	 * @param json the JSON representation
	 * @throws InconsistentJsonException if the JSON representation is inconsistent
	 */
	public NodeInfoImpl(NodeInfoJson json) throws InconsistentJsonException {
		this(
			Objects.requireNonNull(json.getVersion(), "version cannot be null", InconsistentJsonException::new).unmap(),
			mkUuid(json.getUuid()),
			mkLocalDateTimeUTCUuid(json.getLocalDateTimeUTC()),
			json.getMaxChainPortionLength(),
			json.getMaxMempoolPortionLength()
		);
	}

	private static LocalDateTime mkLocalDateTimeUTCUuid(String localDateTimeUTC) throws InconsistentJsonException {
		try {
			return LocalDateTime.parse(Objects.requireNonNull(localDateTimeUTC, "localDateTimeUTC cannot be null", InconsistentJsonException::new), ISO_LOCAL_DATE_TIME);
		}
		catch (DateTimeParseException e) {
			throw new InconsistentJsonException(e);
		}
	}

	private static UUID mkUuid(String uuid) throws InconsistentJsonException {
		try {
			return UUID.fromString(Objects.requireNonNull(uuid, "uuid cannot be null", InconsistentJsonException::new));
		}
		catch (IllegalArgumentException e) {
			throw new InconsistentJsonException(e);
		}
	}

	/**
	 * Creates the message.
	 * 
	 * @param <E> the type of the exception thrown if some argument is illegal
	 * @param <T> the type used for the elements of {@code hashes}
	 * @param hashes the hashes in the message
	 * @param transformation a transformation to apply to each element of {@code hashes} in order to transform them into a byte array
	 * @param onIllegalArgs the creator of the exception thrown if some argument is illegal
	 * @throws E if some argument is illegal
	 */
	private <E extends Exception> NodeInfoImpl(Version version, UUID uuid, LocalDateTime localDateTimeUTC, int maxChainPortionLength, int maxMempoolPortionLength, ExceptionSupplierFromMessage<? extends E> onIllegalArgs) throws E {
		this.version = Objects.requireNonNull(version, "version cannot be null", onIllegalArgs);
		this.uuid = Objects.requireNonNull(uuid, "uuid cannot be null", onIllegalArgs);
		this.localDateTimeUTC = Objects.requireNonNull(localDateTimeUTC, "localDateTimeUTC cannot be null", onIllegalArgs);

		if (maxChainPortionLength < 2)
			throw onIllegalArgs.apply("maxChainPortionLength must be at least 2");

		this.maxChainPortionLength = maxChainPortionLength;

		if (maxMempoolPortionLength < 1)
			throw onIllegalArgs.apply("maxMempoolPortionLength must be at least 1");

		this.maxMempoolPortionLength = maxMempoolPortionLength;
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
	public int getMaxChainPortionLength() {
		return maxChainPortionLength;
	}

	@Override
	public int getMaxMempoolPortionLength() {
		return maxMempoolPortionLength;
	}

	@Override
	public boolean equals(Object other) {
		return other instanceof NodeInfo ni &&
			uuid.equals(ni.getUUID()) && version.equals(ni.getVersion()) &&
			maxChainPortionLength == ni.getMaxChainPortionLength() &&
			maxMempoolPortionLength == ni.getMaxMempoolPortionLength() &&
			localDateTimeUTC.equals(ni.getLocalDateTimeUTC());
	}

	@Override
	public int hashCode() {
		return uuid.hashCode() ^ localDateTimeUTC.hashCode() ^ maxChainPortionLength ^ maxMempoolPortionLength;
	}

	@Override
	public String toString() {
		return "version: " + version + ", UUID: " + uuid + ", UTC date and time: " + localDateTimeUTC + ", max chain portion length: " + maxChainPortionLength + ", max mempool portion length: " + maxMempoolPortionLength;
	}
}