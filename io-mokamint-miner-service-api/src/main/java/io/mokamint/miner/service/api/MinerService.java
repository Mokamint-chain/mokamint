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

package io.mokamint.miner.service.api;

import java.math.BigInteger;
import java.security.PublicKey;
import java.util.Optional;
import java.util.concurrent.TimeoutException;

import io.hotmoka.crypto.api.SignatureAlgorithm;
import io.hotmoka.websockets.client.api.Remote;
import io.mokamint.miner.api.ClosedMinerException;
import io.mokamint.miner.api.MiningSpecification;

/**
 * A websocket client that connects to a remote miner exported
 * by some Mokamint node. It is an adapter of a miner into a web service client.
 */
public interface MinerService extends Remote {

	/**
	 * Yields the specification about the miner connected to this service.
	 * It describes the kind of deadlines that are expected by that miner.
	 * 
	 * @return the mining specification of the miner connected to this service
	 * @throws ClosedMinerException if this service has been closed
	 * @throws TimeoutException if the operation does not terminate inside the expected time window
	 * @throws InterruptedException if the operation is interrupted before being completed
	 */
	MiningSpecification getMiningSpecification() throws ClosedMinerException, TimeoutException, InterruptedException;

	/**
	 * Yields the balance of the given public key. The key typically identifies
	 * a miner. Note that <i>balance</i> is an
	 * application-specification concept, therefore applications might not have
	 * anything to answer here. Moreover, miners are free to implement
	 * an answer here or just return the empty result. This is why the result of this method is optional.
	 * 
	 * @param signature the signature algorithm of {@code publicKey}
	 * @param publicKey the public key whose balance is requested
	 * @return the balance of {@code key}, if any
	 * @throws ClosedMinerException if this miner has been closed
	 * @throws TimeoutException if the operation does not terminate inside the expected time window
	 * @throws InterruptedException if the operation is interrupted before being completed
	 */
	Optional<BigInteger> getBalance(SignatureAlgorithm signature, PublicKey publicKey) throws ClosedMinerException, TimeoutException, InterruptedException;

	/**
	 * Closes the service.
	 */
	@Override
	void close();
}