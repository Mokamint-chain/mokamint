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

import java.net.URI;

import io.hotmoka.annotations.Immutable;
import io.hotmoka.marshalling.api.Marshallable;

/**
 * A peer of a node. It is the address of another node connected to the node.
 * In general, a node is connected to zero or more peers. Peers are compared
 * by URL string.
 */
@Immutable
public interface Peer extends Marshallable, Comparable<Peer> {

	/**
	 * Yields the URI of this peer. It is the network address (including the port, if any)
	 * where the peer can be contacted by websockets. Consequently, it always starts with {@code ws://}.
	 * 
	 * @return the URI
	 */
	URI getURI();

	@Override
	boolean equals(Object obj);

	@Override
	int hashCode();

	@Override
	String toString();

	/**
	 * Yields a string representation of this peer, sanitized to a maximal
	 * length, so that it cannot be used, for instance, for filling the logs
	 * with very long strings.
	 * 
	 * @return the sanitized representation
	 */
	String toStringSanitized();
}