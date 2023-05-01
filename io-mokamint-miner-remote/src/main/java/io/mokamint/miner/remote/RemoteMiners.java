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
import java.net.URI;

import io.mokamint.miner.api.Miner;
import io.mokamint.miner.remote.internal.RemoteMinerImpl;
import jakarta.websocket.DeploymentException;

/**
 * Provider of a miner that connects to a remote mining service.
 */
public interface RemoteMiners {

	/**
	 * Yields a new remote miner.
	 * 
	 * @param uri the URI where the mining service should be published
	 * @return the new remote miner
	 * @throws DeploymentException if the remote miner could not be deployed
	 * @throws IOException 
	 */
	static Miner of(URI uri) throws DeploymentException, IOException {
		return new RemoteMinerImpl(uri);
	}
}
