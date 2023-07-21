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

import java.util.function.Consumer;
import java.util.stream.Stream;

import io.hotmoka.annotations.ThreadSafe;

/**
 * A node that sends and receives acknowledgments about new peers,
 * new transactions and new blocks.
 */
@ThreadSafe
public interface WhisperingNode {

	/**
	 * Register the given listener for being called when this node whispers some peers.
	 * 
	 * @param listener the listener
	 */
	void addOnWhisperPeersListener(Consumer<Stream<Peer>> listener);

	/**
	 * Unregister the given listener from those called when this node whispers some peers.
	 * If it is not registered, nothing happens.
	 * 
	 * @param listener the listener
	 */
	void removeOnWhisperPeersListener(Consumer<Stream<Peer>> listener);
}