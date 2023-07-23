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

import java.util.stream.Stream;

import io.mokamint.node.api.Peer;
import io.mokamint.node.api.PublicNode;
import io.mokamint.node.messages.api.Whisperer;

/**
 * The internal API of a public Mokamint node. It includes methods that are not
 * exported to the general users, but only used in the implementations of the nodes.
 */
public interface PublicNodeInternals extends NodeInternals, PublicNode, Whisperer {

	/**
	 * Binds a whisperer to this node. This means that whenever this node
	 * has something to whisperer, it will whisper to {@code whisperer} as well.
	 * Note that this method does not state the converse.
	 * 
	 * @param whisperer the whisperer to bind
	 */
	void bindWhisperer(Whisperer whisperer);

	/**
	 * Unbinds a whisperer to this node. This means that this node will stop
	 * whispering to {@code whisperer}.
	 * 
	 * @param whisperer the whisperer to unbind
	 */
	void unbindWhisperer(Whisperer whisperer);

	/**
	 * Called when some peers must be whispered to the peers of this node.
	 * 
	 * @param peers the peers to whisper
	 */
	void whisperToPeers(Stream<Peer> peers);
}