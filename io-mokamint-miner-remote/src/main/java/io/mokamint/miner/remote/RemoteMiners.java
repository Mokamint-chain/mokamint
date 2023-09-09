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

package io.mokamint.miner.remote;

import java.io.IOException;
import java.security.PublicKey;

import io.mokamint.miner.api.Miner;
import io.mokamint.miner.remote.internal.RemoteMinerImpl;
import jakarta.websocket.DeploymentException;

/**
 * Provider of a miner that connects to a remote mining service.
 */
public interface RemoteMiners {

	/**
	 * Yields and opens a new remote miner.
	 * 
	 * @param port the http port where the server is opened on localhost
	 * @return the new remote miner
	 * @param chainId the chain identifier of the blockchain for which the deadlines will be used
	 * @param nodePublicKey the public key of the node for which the deadlines are computed
	 * @throws DeploymentException if the remote mining endpoint could not be deployed
	 * @throws IOException if an I/O error occurs
	 */
	static Miner of(int port, String chainId, PublicKey nodePublicKey) throws DeploymentException, IOException {
		return new RemoteMinerImpl(port, chainId, nodePublicKey);
	}
}