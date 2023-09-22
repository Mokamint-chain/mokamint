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

package io.mokamint.node.local.api;

import io.hotmoka.annotations.ThreadSafe;
import io.mokamint.miner.api.Miner;
import io.mokamint.node.api.ClosedNodeException;
import io.mokamint.node.api.FullNode;
import io.mokamint.node.api.PublicNodeInternals;

/**
 * A local node of a Mokamint blockchain.
 */
@ThreadSafe
public interface LocalNode extends PublicNodeInternals, FullNode {

	@Override
	LocalNodeConfig getConfig();

	/**
	 * Adds the given miner to the miners of this node.
	 * The responsibility of closing the miner passes to the node:
	 * the miner will be closed when the node will be closed.
	 * 
	 * @param miner the miner
	 * @return true if and only if the miner has been added
	 * @throws ClosedNodeException if this node is closed
	 */
	boolean add(Miner miner) throws ClosedNodeException;
}