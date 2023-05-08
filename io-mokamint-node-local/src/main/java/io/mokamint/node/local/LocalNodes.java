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

import java.security.NoSuchAlgorithmException;

import io.mokamint.application.api.Application;
import io.mokamint.miner.api.Miner;
import io.mokamint.node.api.Node;
import io.mokamint.node.local.internal.LocalNodeImpl;

/**
 * A provider of local nodes.
 */
public interface LocalNodes {

	/**
	 * Yields a local node of a Mokamint blockchain, for the given application.
	 * 
	 * @param config the configuration of the node
	 * @param app the application
	 * @param miners the miners
	 * @return the local node
	 * @throws NoSuchAlgorithmException if some block in the database uses an unknown hashing algorithm
	 */
	static Node of(Config config, Application app, Miner... miners) throws NoSuchAlgorithmException {
		return new LocalNodeImpl(config, app, miners);
	}
}