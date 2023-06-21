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

import java.util.concurrent.TimeoutException;
import java.util.stream.Stream;

import io.hotmoka.annotations.ThreadSafe;

/**
 * The restricted interface of a node of a Mokamint blockchain.
 * Typically, this API can be called from the local machine only.
 */
@ThreadSafe
public interface RestrictedNode extends AutoCloseableNode {

	/**
	 * Adds the given peers to the set of peers of this node.
	 * 
	 * @param peers the peers to add
	 * @throws TimeoutException if no answer arrives before a time window
	 * @throws InterruptedException if the current thread is interrupted while waiting for an answer to arrive
	 */
	void addPeers(Stream<Peer> peers) throws TimeoutException, InterruptedException;

	/**
	 * Removes the given peers from the set of peers of this node.
	 * 
	 * @param peers the peers to remove
	 * @throws TimeoutException if no answer arrives before a time window
	 * @throws InterruptedException if the current thread is interrupted while waiting for an answer to arrive
	 */
	void removePeers(Stream<Peer> peers) throws TimeoutException, InterruptedException;
}