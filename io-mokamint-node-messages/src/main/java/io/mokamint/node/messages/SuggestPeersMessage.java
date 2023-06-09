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

package io.mokamint.node.messages;

import java.util.stream.Stream;

import io.mokamint.node.api.Peer;

/**
 * The network message sent by a public node service
 * to the connected remote nodes, to signify that a new peer has been added to
 * the serviced node.
 */
public interface SuggestPeersMessage {

	/**
	 * Yields the peers suggested for addition.
	 * 
	 * @return the peers
	 */
	Stream<Peer> getPeers();

	@Override
	boolean equals(Object obj);
}