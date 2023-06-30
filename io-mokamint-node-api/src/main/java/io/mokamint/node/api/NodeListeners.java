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

/**
 * The listeners of a node.
 */
public interface NodeListeners {

	/**
	 * Register the given listener for being called when a peer
	 * is added to the node.
	 * 
	 * @param listener the listener
	 */
	void addOnPeerAddedListener(Consumer<Peer> listener);

	/**
	 * Unregister the given listener from those called when a peer
	 * is added to the node.
	 * 
	 * @param listener the listener
	 */
	void removeOnPeerAddedListener(Consumer<Peer> listener);
}