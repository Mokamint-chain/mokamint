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

package io.mokamint.node;

import java.util.function.Consumer;
import java.util.stream.Stream;

import io.mokamint.node.api.Peer;
import io.mokamint.node.api.PublicNode;

/**
 * The internal API of a public Mokamint node. It includes methods that are not
 * exported to the general users, but only used in the implementations of the nodes.
 */
public interface PublicNodeInternals extends NodeInternals, PublicNode {

	/**
	 * Takes note that the given code must be executed when this node
	 * has some peers to whisper.
	 * 
	 * @param handler the code
	 */
	void addOnWhisperPeersHandler(Consumer<Stream<Peer>> handler);

	/**
	 * Removes the given code from that executed when this node
	 * has some peers to whisper.
	 * 
	 * @param handler the code
	 */
	void removeOnWhisperPeersHandler(Consumer<Stream<Peer>> handler);

	/**
	 * Whisper some peers to the node.
	 * 
	 * @param peers the peers
	 */
	void receiveWhisperedPeers(Stream<Peer> peers);
}