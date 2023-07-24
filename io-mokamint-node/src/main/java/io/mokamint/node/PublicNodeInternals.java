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

import java.util.function.Predicate;

import io.mokamint.node.api.PublicNode;
import io.mokamint.node.messages.api.WhisperPeersMessage;
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
	 * Whisper the given message. This is a special case of {@link Whisperer#whisper(WhisperPeersMessage, Predicate)}
	 * when it is known that the whispered peers are all the same whisperer that
	 * receives the call. In some cases, this can be useful for optimization. For instance,
	 * if the whisperer is a local node, that it needn't try to add the peers
	 * among its peers, since they would end up being rejected (a peer cannot be
	 * added to itself).
	 * 
	 * @param message the message
	 * @param seen a predicate telling if a whisperer has already whispered the
	 *             {@code message}. This is used in order to avoid infinite recursion
	 *             if whisperers form a cycle
	 */
	void whisperItself(WhisperPeersMessage message, Predicate<Whisperer> seen);
}