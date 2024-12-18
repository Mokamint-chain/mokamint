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

package io.mokamint.node.local;

import java.security.KeyPair;
import java.util.concurrent.TimeoutException;

import io.mokamint.application.api.Application;
import io.mokamint.node.api.NodeException;
import io.mokamint.node.local.api.LocalNode;
import io.mokamint.node.local.api.LocalNodeConfig;
import io.mokamint.node.local.internal.LocalNodeImpl;

/**
 * A provider of local nodes.
 */
public abstract class LocalNodes {

	private LocalNodes() {}

	/**
	 * Yields a local node of a Mokamint blockchain, for the given application.
	 * 
	 * @param config the configuration of the node
	 * @param keyPair the key pair that the node will use to sign the blocks that it mines
	 * @param app the application
	 * @param init if true, creates a genesis block and starts mining on top
	 *             (initial synchronization is consequently skipped)
	 * @return the local node
	 * @throws InterruptedException if the initialization of the node was interrupted
	 * @throws TimeoutException if the application did not answer in time
	 * @throws NodeException if the node is misbehaving
	 */
	public static LocalNode of(LocalNodeConfig config, KeyPair keyPair, Application app, boolean init) throws InterruptedException, TimeoutException, NodeException {
		return new LocalNodeImpl(config, keyPair, app, init);
	}
}