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

package io.mokamint.miner.service;

import java.net.URI;

import io.mokamint.miner.api.Miner;
import io.mokamint.miner.api.MinerException;
import io.mokamint.miner.service.api.MinerService;
import io.mokamint.miner.service.internal.MinerServiceImpl;

/**
 * Providers of miner services.
 */
public final class MinerServices {

	private MinerServices() {}

	/**
	 * Opens and yields a new miner service that adapts the given miner and connects it to the given URI of a remote miner.
	 * 
	 * @param miner the adapted miner
	 * @param uri the websockets URI of the remote miner. For instance: {@code ws://my.site.org:8025}
	 * @return the miner service
	 * @throws MinerException if the service cannot be deployed
	 */
	public static MinerService of(Miner miner, URI uri) throws MinerException {
		return new MinerServiceImpl(miner, uri);
	}

	/**
	 * Opens and yields a miner service without a miner. It won't provide deadlines to the connected
	 * remote miner, by allows one to call the methods of the remote miner anyway.
	 * 
	 * @param uri the websockets URI of the remote miner. For instance: {@code ws://my.site.org:8025}
	 * @throws MinerException if the service cannot be deployed
	 */
	public static MinerService of(URI uri) throws MinerException {
		return new MinerServiceImpl(uri);
	}
}