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

import java.util.function.Predicate;

/**
 * An object that whispers peers, transactions and blocks.
 */
public interface Whisperer {

	/**
	 * Whisper the given peers.
	 * 
	 * @param whisperedPeers the whispered peers
	 * @param seen a predicate telling if a whisperer has already whispered the
	 *             {@code whisperedPeers}. This is used in order to avoid infinite recursion
	 *             if whisperers form a cycle inside the same machine (this does not account
	 *             for network connections among whisperers)
	 */
	void whisper(WhisperedPeers whisperedPeers, Predicate<Whisperer> seen);

	/**
	 * Whisper the given block.
	 * 
	 * @param whisperedBlock the whispered block
	 * @param seen a predicate telling if a whisperer has already whispered the
	 *             {@code whisperedBlock}. This is used in order to avoid infinite recursion
	 *             if whisperers form a cycle inside the same machine (this does not account
	 *             for network connections among whisperers)
	 */
	void whisper(WhisperedBlock whisperedBlock, Predicate<Whisperer> seen);

	/**
	 * Whisper the given transaction.
	 * 
	 * @param whisperedTransaction the whispered transaction
	 * @param seen a predicate telling if a whisperer has already whispered the
	 *             {@code whisperedTransaction}. This is used in order to avoid infinite recursion
	 *             if whisperers form a cycle inside the same machine (this does not account
	 *             for network connections among whisperers)
	 */
	void whisper(WhisperedTransaction whisperedTransaction, Predicate<Whisperer> seen);

	/**
	 * Whisper the given peer. This is a special case of {@link #whisper(WhisperedPeers, Predicate)}
	 * when it is known that the whispered peers are actually a single peer, the one that
	 * receives the call. In some cases, this can be useful for optimization. For instance,
	 * if the whisperer is a local node, then it needn't try to add the peers
	 * among its peers, since they would end up being rejected (a peer cannot be added to itself).
	 * 
	 * @param itself the whispered peer itself
	 * @param seen a predicate telling if a whisperer has already whispered
	 *             {@code itself}. This is used in order to avoid infinite recursion
	 *             if whisperers form a cycle
	 */
	void whisperItself(WhisperedPeers itself, Predicate<Whisperer> seen);
}