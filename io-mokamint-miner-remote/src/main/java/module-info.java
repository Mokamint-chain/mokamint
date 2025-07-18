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

/**
 * This module implements a remote miner, that is, a miner that works
 * by forwarding its work to remote services connected by network.
 */
module io.mokamint.miner.remote {
	exports io.mokamint.miner.remote;
	// needed to allow the endpoint to be created by reflection although it is not exported
	opens io.mokamint.miner.remote.internal to org.glassfish.tyrus.core;

	requires transitive io.mokamint.miner.remote.api;
	requires transitive io.mokamint.miner.api;
	requires io.mokamint.miner.messages;
	requires io.mokamint.nonce;
	requires io.hotmoka.websockets.client.api;
	requires io.hotmoka.websockets.server;
	requires io.hotmoka.websockets.beans;
	requires org.glassfish.tyrus.core;
	requires io.hotmoka.annotations;
	requires java.logging;

	// only used for testing
	requires static io.hotmoka.crypto;
	requires static io.mokamint.miner;
}