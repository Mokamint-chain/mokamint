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

import java.util.UUID;

import io.hotmoka.annotations.Immutable;

/**
 * Information about a miner of a node. Miner information is ordered first
 * by points (decreasing), then by {@link #getUUID()}.
 */
@Immutable
public interface MinerInfo extends Comparable<MinerInfo> {

	/**
	 * Yields the identifier of the miner.
	 * 
	 * @return the identifier of the miner
	 */
	UUID getUUID();

	/**
	 * Yields the points of the miner. They are an estimation of how much well the
	 * miner behaved recently.
	 *
	 * @return the points; this should always be positive
	 */
	long getPoints();

	/**
	 * Yields a description of the miner.
	 * 
	 * @return the description
	 */
	String getDescription();

	@Override
	boolean equals(Object obj);

	@Override
	int hashCode();

	@Override
	String toString();
}