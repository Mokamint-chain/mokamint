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
 * This module implements a Mokamint local node, that is, a node
 * that actually works on the local machine where it is executed.
 */
module io.mokamint.node.local {
	exports io.mokamint.node.local;

	requires transitive io.mokamint.node.local.api;
	requires transitive io.mokamint.application.api;
	requires io.mokamint.node;
	requires io.mokamint.miner.remote;
	requires io.mokamint.node.remote;
	requires io.mokamint.node.service.api;
	requires io.mokamint.node.messages;
	requires io.hotmoka.annotations;
	requires io.hotmoka.exceptions;
	requires io.hotmoka.closeables;
	requires io.hotmoka.xodus;
	requires io.hotmoka.marshalling;
	requires io.hotmoka.crypto;
	requires toml4j;
	requires java.logging;
	requires jdk.unsupported; // because xodus needs sl4j that needs sun.misc.Unsafe

	requires static io.mokamint.nonce;
}