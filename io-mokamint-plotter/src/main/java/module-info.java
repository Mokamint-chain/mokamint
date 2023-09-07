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
 * This module implements the plots of a Mokamint blockchain.
 */
module io.mokamint.plotter {
	exports io.mokamint.plotter;

	requires transitive io.mokamint.plotter.api;
	requires transitive io.hotmoka.marshalling.api;
	requires io.mokamint.nonce;
	requires io.hotmoka.crypto;
	requires io.hotmoka.exceptions;
	requires io.hotmoka.annotations;
	requires java.logging;
}