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
 * This module implements a miner service, that is, a network service that
 * provides mining capabilities by network.
 */
module io.mokamint.miner.service {
	exports io.mokamint.miner.service;
	requires transitive io.mokamint.miner.service.api;
	requires transitive io.mokamint.miner.api;
	requires transitive io.hotmoka.websockets.client;
	requires io.mokamint.nonce;
	requires java.logging;
}