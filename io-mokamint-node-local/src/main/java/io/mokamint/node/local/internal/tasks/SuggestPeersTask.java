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

package io.mokamint.node.local.internal.tasks;

import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.stream.Stream;

import io.hotmoka.annotations.OnThread;
import io.mokamint.node.api.Peer;
import io.mokamint.node.local.internal.LocalNodeImpl;
import io.mokamint.node.local.internal.LocalNodeImpl.Task;

/**
 * A task that suggests peers to other peers.
 */
public class SuggestPeersTask extends Task {

	/**
	 * The peers to suggest.
	 */
	private final Peer[] peers;

	/**
	 * A supplier of the listeners registered in the node, eager to receive peer addition suggestions.
	 */
	private final Supplier<Stream<Consumer<Stream<Peer>>>> listeners;

	/**
	 * Creates a task that suggests peers to other peers.
	 * 
	 * @param peers the peers to suggest
	 * @param listeners the listeners registered in the node, eager to receive peer addition suggestions
	 * @param node the node for which this task is working
	 */
	public SuggestPeersTask(Stream<Peer> peers, Supplier<Stream<Consumer<Stream<Peer>>>> listeners, LocalNodeImpl node) {
		node.super();

		this.peers = peers.toArray(Peer[]::new);
		this.listeners = listeners;
	}

	@Override
	public String toString() {
		return "suggest " + peers.length + " peers to the peers connected to the node";
	}

	@Override @OnThread("tasks")
	protected void body() {
		listeners.get().forEach(listener -> listener.accept(Stream.of(peers)));
	}
}