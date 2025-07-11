/*
Copyright 2024 Fausto Spoto

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

package io.mokamint.miner.remote.api;

import io.hotmoka.annotations.ThreadSafe;
import io.mokamint.miner.api.Miner;

/**
 * A remote miner. It publishes an endpoint at a URL,
 * where mining services can connect and provide their deadlines.
 */
@ThreadSafe
public interface RemoteMiner extends Miner {

	/**
	 * The network endpoint path where challenges are sent from a remote miner
	 * to a miner service, and deadlines back from a miner service to a remote miner.
	 */
	String MINING_ENDPOINT = "/mining";

	/**
	 * The network endpoint path where {@link Miner#getMiningSpecification()} is published.
	 */
	String GET_MINING_SPECIFICATION_ENDPOINT = "/get_mining_specification";
}