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

import java.io.IOException;
import java.net.URISyntaxException;
import java.security.NoSuchAlgorithmException;

import io.mokamint.application.api.Application;
import io.mokamint.miner.api.Miner;
import io.mokamint.node.api.DatabaseException;
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
	 * @param init if the blockchain database is empty, it requires to initialize the blockchain
	 *             by mining a genesis block. Otherwise, it is ignored
	 * @param miners the miners
	 * @return the local node
	 * @throws NoSuchAlgorithmException if some block in the database uses an unknown hashing algorithm
	 * @throws DatabaseException if the database is corrupted
	 * @throws IOException if the database is corrupted
	 * @throws URISyntaxException if some URI in the database has an illegal syntax
	 */
	static LocalNode of(Config config, Application app, boolean init, Miner... miners)
			throws NoSuchAlgorithmException, DatabaseException, IOException, URISyntaxException {

		return new LocalNodeImpl(config, app, init, miners);
	}
}