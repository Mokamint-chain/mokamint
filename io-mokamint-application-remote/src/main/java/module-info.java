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
 * This module implements Mokamint applications that forward all calls to a network application service.
 */
module io.mokamint.application.remote {
	exports io.mokamint.application.remote;

	requires transitive io.mokamint.application.remote.api;
	requires io.mokamint.application;
	requires io.mokamint.application.service.api;
	requires io.mokamint.application.messages;
	requires io.hotmoka.websockets.client;
	requires io.hotmoka.annotations;
	requires io.hotmoka.closeables;
	requires java.logging;
}