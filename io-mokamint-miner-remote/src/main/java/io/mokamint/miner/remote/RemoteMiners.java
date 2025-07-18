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

import io.hotmoka.websockets.api.FailedDeploymentException;
import io.mokamint.miner.api.MiningSpecification;
import io.mokamint.miner.remote.api.DeadlineValidityCheck;
import io.mokamint.miner.remote.api.RemoteMiner;
import io.mokamint.miner.remote.internal.RemoteMinerImpl;

/**
 * Provider of a miner that connects to a remote mining service.
 */
public abstract class RemoteMiners {

	private RemoteMiners() {}

	/**
	 * Yields and opens a new remote miner.
	 * 
	 * @param port the http port where the server is opened on localhost
	 * @param miningSpecification the specification of the mining parameters; all services
	 *                            that connect to this remote must provided deadlines
	 *                            that comply with this specification
	 * @param check an algorithm to check if a deadline is valid
	 * @return the new remote miner
	 * @throws FailedDeploymentException if the remote miner could not be deployed
	 */
	public static RemoteMiner open(int port, MiningSpecification miningSpecification, DeadlineValidityCheck check) throws FailedDeploymentException {
		return new RemoteMinerImpl(port, miningSpecification, check);
	}
}