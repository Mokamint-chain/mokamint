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

package io.mokamint.node.messages.api;

import java.util.function.Predicate;

/**
 * An object that whispers peers, transactions and blocks.
 * These are for instance local nodes, public remotes and public services.
 */
public interface Whisperer {

	/**
	 * Whisper the given message.
	 * 
	 * @param message the message
	 * @param seen a predicate telling if a whisperer has already whispered the
	 *             {@code message}. This is used in order to avoid infinite recursion
	 *             if whisperers form a cycle
	 */
	void whisper(WhisperPeersMessage message, Predicate<Whisperer> seen);

	/**
	 * Whisper the given message. This is a special case of {@link #whisper(WhisperPeersMessage, Predicate)}
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