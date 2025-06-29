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
 * This module defines the API of the network messages exchanged between a miner service and remote.
 */
module io.mokamint.miner.messages.api {
	exports io.mokamint.miner.messages.api;

	requires transitive io.mokamint.miner.api;
	requires transitive io.hotmoka.websockets.beans.api;
	requires io.hotmoka.annotations;
}