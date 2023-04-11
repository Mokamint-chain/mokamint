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

package io.mokamint.miner.remote.internal;

import java.net.URI;
import java.util.function.BiConsumer;
import java.util.logging.Logger;

import io.mokamint.miner.api.Miner;
import io.mokamint.nonce.api.Deadline;

/**
 * The implementation of a local miner.
 * It uses a set of plot files to find deadlines on-demand.
 */
public class RemoteMinerImpl implements Miner {
	private final static Logger LOGGER = Logger.getLogger(RemoteMinerImpl.class.getName());

	public RemoteMinerImpl(URI uri) {
	}

	@Override
	public void requestDeadline(int scoopNumber, byte[] data, BiConsumer<Deadline, Miner> onDeadlineComputed) {
		
	}

	@Override
	public void close() {
	}
}