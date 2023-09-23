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
 * This module implements Mokamint nodes that forwards all calls to a network node service.
 */
module io.mokamint.node.remote {
	exports io.mokamint.node.remote;

	requires io.mokamint.node;
	requires io.mokamint.node.service.api;
	requires io.mokamint.node.messages;
	requires io.hotmoka.crypto.api;
	requires io.hotmoka.websockets.client;
	requires io.hotmoka.annotations;
	requires java.logging;
}