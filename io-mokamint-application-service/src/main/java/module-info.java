/*
Copyright 2024 Fausto Spoto

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
 * This module defines the implementation of an application service, that is,
 * a network server that provides the API of a Mokamint application.
 */
module io.mokamint.application.service {
	exports io.mokamint.application.service;

	// needed to allow the endpoints to be created by reflection although they are not exported
	opens io.mokamint.application.service.internal to org.glassfish.tyrus.core;

	requires transitive io.mokamint.application.service.api;
	requires transitive io.mokamint.application.api;
	requires io.mokamint.application.messages;
	requires io.hotmoka.websockets.server;
	requires io.hotmoka.annotations;
	requires org.glassfish.tyrus.core;
	requires java.logging;
}