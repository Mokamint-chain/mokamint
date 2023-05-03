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

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;

import io.mokamint.miner.api.Miner;
import io.mokamint.miner.service.api.MinerService;
import io.mokamint.miner.service.internal.MinerServiceImpl;
import jakarta.websocket.DeploymentException;

/**
 * Providers of miner services.
 */
public interface MinerServices {

	/**
	 * Creates a mining service that adapts the given miner and connects it
	 * to the given URI of a remote miner.
	 * 
	 * @param miner the adapted miner
	 * @param uri the websockets URI of the remote miner. For instance: {@code ws://my.site.org:8025}
	 */
	static MinerService adapt(Miner miner, URI uri) throws DeploymentException, IOException, URISyntaxException {
		return new MinerServiceImpl(miner, uri);
	}
}
