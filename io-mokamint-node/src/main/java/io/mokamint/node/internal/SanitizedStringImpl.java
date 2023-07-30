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

package io.mokamint.node.internal;

import java.util.stream.Collectors;
import java.util.stream.Stream;

import io.mokamint.node.SanitizedString;
import io.mokamint.node.api.Peer;

/**
 * A sanitized string representation, that can be reported to the
 * logs avoiding the risk of log injection.
 */
public class SanitizedStringImpl implements SanitizedString {

	private final String sanitized;

	/**
	 * Constructs a sanitized representation of the given peers. It contains their
	 * URL, truncated to a maximal allowed length and for a maximal number of peers only.
	 */
	public SanitizedStringImpl(Stream<Peer> peers) {
		this.sanitized = sanitize(peers);
	}

	/**
	 * Yields the sanitized representation computed by this object.
	 * 
	 * @return the sanitized representation
	 */
	@Override
	public String toString() {
		return sanitized;
	}

	/**
	 * Yields a string describing some peers. It truncates peers too long
	 * or too many peers, in order to cope with potential log injections.
	 * 
	 * @return the string
	 */
	private static String sanitize(Stream<Peer> peers) {
		var peersAsArray = peers.toArray(Peer[]::new);
		String result = Stream.of(peersAsArray).limit(20).map(SanitizedStringImpl::truncate).collect(Collectors.joining(", "));
		if (peersAsArray.length > 20)
			result += ", ...";
	
		return result;
	}

	private static String truncate(Peer peer) {
		String uri = peer.toString();
		if (uri.length() > 50)
			return uri.substring(0, 50) + "...";
		else
			return uri;
	}
}
