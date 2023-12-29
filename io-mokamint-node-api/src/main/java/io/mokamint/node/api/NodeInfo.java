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

package io.mokamint.node.api;

import java.time.LocalDateTime;
import java.util.UUID;

import io.hotmoka.annotations.Immutable;

/**
 * Non-consensus information about a node. Nodes of the same network can have different
 * non-consensus information.
 */
@Immutable
public interface NodeInfo {

	/**
	 * Yields the version of the node.
	 * 
	 * @return the version
	 */
	Version getVersion();

	/**
	 * Yields the UUID (unique identifier) of the node.
	 * 
	 * @return the UUID
	 */
	UUID getUUID();

	/**
	 * Yields the date and time of the node.
	 * 
	 * @return the date and time of the node, in UTC
	 */
	LocalDateTime getLocalDateTimeUTC();

	@Override
	boolean equals(Object other);

	@Override
	int hashCode();

	@Override
	String toString();
}