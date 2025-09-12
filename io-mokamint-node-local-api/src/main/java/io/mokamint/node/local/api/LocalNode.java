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

import java.util.Optional;

import io.hotmoka.annotations.ThreadSafe;
import io.mokamint.application.api.Application;
import io.mokamint.miner.api.Miner;
import io.mokamint.node.api.ClosedNodeException;
import io.mokamint.node.api.FullNode;
import io.mokamint.node.api.MinerInfo;

/**
 * A local node of a Mokamint blockchain.
 */
@ThreadSafe
public interface LocalNode extends FullNode {

	/**
	 * Yields the application running over this node.
	 * 
	 * @return the application
	 */
	Application getApplication();

	/**
	 * Yields the configuration of this node.
	 * 
	 * @return the configuration of this node
	 * @throws ClosedNodeException if the node has been already closed
	 */
	@Override
	LocalNodeConfig getConfig() throws ClosedNodeException;

	/**
	 * Adds the given miner to the miners of this node.
	 * 
	 * @param miner the miner
	 * @return the information about the added miner; this is empty if the miner has not been added
	 * @throws ClosedNodeException if the node has been already closed
	 */
	Optional<MinerInfo> add(Miner miner) throws ClosedNodeException;
}