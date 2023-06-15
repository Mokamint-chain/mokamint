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

import io.hotmoka.annotations.ThreadSafe;

/**
 * The restricted interface of a node of a Mokamint blockchain.
 * Typically, this API can be called from the local machine only.
 */
@ThreadSafe
public interface RestrictedNode extends AutoCloseable {

	/**
	 * Adds the given peer to the set of peers of this node.
	 * 
	 * @param peer the peer to add
	 */
	void addPeer(Peer peer);

	/**
	 * Removes the given peer from the set of peers of this node.
	 * 
	 * @param peer the peer to remove
	 */
	void removePeer(Peer peer);
}