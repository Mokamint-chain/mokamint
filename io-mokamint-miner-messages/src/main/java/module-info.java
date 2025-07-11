/*
Copyright 2025 Fausto Spoto

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
 * This module implements the network messages exchanged between a node service and remote.
 */
module io.mokamint.miner.messages {
	exports io.mokamint.miner.messages;
	// beans must be accessible, encoded and decoded by reflection through Gson
	opens io.mokamint.miner.messages.internal.json to com.google.gson;

	requires transitive io.mokamint.miner.messages.api;
	requires io.mokamint.miner;
	requires io.hotmoka.exceptions;
	requires io.hotmoka.crypto;
	requires io.hotmoka.websockets.beans;
	requires com.google.gson;

	// this makes sun.misc.Unsafe accessible, so that Gson can instantiate
	// classes without the no-args constructor
	requires jdk.unsupported;
}