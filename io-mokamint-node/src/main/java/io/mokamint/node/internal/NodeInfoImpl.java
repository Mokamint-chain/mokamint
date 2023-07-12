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
	 * Yields a new node information object.
	 * 
	 * @param version the version of the node
	 * @param uuid the UUID of the node
	 */
	public NodeInfoImpl(Version version, UUID uuid) {
		this.version = version;
		this.uuid = uuid;
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
	public boolean equals(Object other) {
		return other instanceof NodeInfo otherAsNI && uuid.equals(otherAsNI.getUUID()) && version.equals(otherAsNI.getVersion());
	}

	@Override
	public String toString() {
		return "version: " + version + ", UUID: " + uuid;
	}
}