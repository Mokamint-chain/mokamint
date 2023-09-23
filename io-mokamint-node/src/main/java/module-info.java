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
 * This module implements the classes shared by all implementations of a Mokamint node.
 */
module io.mokamint.node {
	exports io.mokamint.node;

	// beans must be accessible, encoded and decoded by reflection through Gson
	opens io.mokamint.node.internal to com.google.gson;
	opens io.mokamint.node.internal.gson to com.google.gson;

	requires transitive io.mokamint.node.api;
	requires transitive io.hotmoka.marshalling.api;
	requires transitive io.mokamint.node.messages.api;
	requires io.mokamint.nonce;
	requires io.hotmoka.marshalling;
	requires io.hotmoka.annotations;
	requires io.hotmoka.websockets.beans;
	requires com.google.gson;
	requires toml4j;
	requires java.logging;
	
	// only needed for testing
	requires static io.hotmoka.crypto;
}