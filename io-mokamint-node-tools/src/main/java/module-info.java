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
 * This module implements a command-line tool for controlling Mokamint nodes.
 */
module io.mokamint.node.tools {
	exports io.mokamint.node.tools;
	
	// needed to inject CLI options
    opens io.mokamint.node.tools.internal to info.picocli;
    opens io.mokamint.node.tools.internal.miners to info.picocli;
    opens io.mokamint.node.tools.internal.peers to info.picocli;
    opens io.mokamint.node.tools.internal.config to info.picocli;
    opens io.mokamint.node.tools.internal.chain to info.picocli;
    opens io.mokamint.node.tools.internal.tasks to info.picocli;
    opens io.mokamint.node.tools.internal.keys to info.picocli;

	requires io.mokamint.node.local;
	requires io.mokamint.application.api;
	requires io.mokamint.miner.local;
	requires io.mokamint.miner.remote;
	requires io.mokamint.node.remote;
	requires io.mokamint.plotter;
	requires io.mokamint.tools;
	requires io.mokamint.node.service;
	requires io.hotmoka.crypto;
	requires io.hotmoka.exceptions;
	requires io.hotmoka.websockets.beans;
	requires java.logging;
}