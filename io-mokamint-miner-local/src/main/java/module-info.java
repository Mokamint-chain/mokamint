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
 * This module implements a local miner, that is, a miner that actually executes
 * on the local machine.
 */
module io.mokamint.miner.local {
	exports io.mokamint.miner.local;

	requires transitive io.mokamint.miner.local.api;
	requires transitive io.mokamint.miner;
	requires transitive io.mokamint.plotter.api;
	requires io.mokamint.nonce.api;
	requires io.hotmoka.crypto.api;
	requires io.hotmoka.exceptions;
	requires java.logging;
}