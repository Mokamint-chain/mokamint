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

import io.hotmoka.annotations.Immutable;

/**
 * Information about a peer of a node. Peer information is ordered first
 * by connection (connected peers before disconnected peers), then
 * by points (decreasing), then by peer and finally by local date and time.
 */
@Immutable
public interface PeerInfo extends Comparable<PeerInfo> {

	/**
	 * Yields the peer described by this information.
	 * 
	 * @return the peer
	 */
	Peer getPeer();

	/**
	 * Yields the points of the peer. They are an estimation of how much well the
	 * peer behaved recently.
	 *
	 * @return the points; this should always be positive
	 */
	long getPoints();

	/**
	 * The connection status of the peer.
	 * 
	 * @return true if and only if the node is connected to the peer
	 */
	boolean isConnected();

	@Override
	boolean equals(Object obj);

	@Override
	int hashCode();

	@Override
	String toString();
}