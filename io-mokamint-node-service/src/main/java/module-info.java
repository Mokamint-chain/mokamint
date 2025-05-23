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
 * This module defines the implementation of a node service, that is,
 * a network server that provides the API of a Mokamint node.
 */
module io.mokamint.node.service {
	exports io.mokamint.node.service;

	// needed to allow the endpoints to be created by reflection although they are not exported
	opens io.mokamint.node.service.internal to org.glassfish.tyrus.core;

	requires transitive io.mokamint.node.service.api;
	requires transitive io.mokamint.node.api;
	requires io.mokamint.miner.api;	
	requires io.mokamint.node.messages;
	requires io.mokamint.node;
	requires io.hotmoka.websockets.server;
	requires io.hotmoka.websockets.beans;
	requires io.hotmoka.annotations;
	requires io.hotmoka.closeables.api;
	requires org.glassfish.tyrus.core;
	requires java.logging;
}