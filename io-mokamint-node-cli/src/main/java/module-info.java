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
module io.mokamint.node.cli {
	exports io.mokamint.node.cli;

	// needed to inject CLI options or JSON serialization
	opens io.mokamint.node.cli.internal to info.picocli;
    opens io.mokamint.node.cli.internal.mempool to info.picocli, com.google.gson;
    opens io.mokamint.node.cli.internal.applications to info.picocli, com.google.gson;
    opens io.mokamint.node.cli.internal.miners to info.picocli, com.google.gson;
    opens io.mokamint.node.cli.internal.peers to info.picocli, com.google.gson;
    opens io.mokamint.node.cli.internal.config to info.picocli;
    opens io.mokamint.node.cli.internal.chain to info.picocli, com.google.gson;
    opens io.mokamint.node.cli.internal.tasks to info.picocli, com.google.gson;
    opens io.mokamint.node.cli.internal.transactions to info.picocli, com.google.gson;

    // for parsing JSON through gson (remove previous opens for com.google.cson as well)
    //opens io.mokamint.node.cli.internal.json to com.google.gson;

    requires transitive io.mokamint.node.cli.api;
    requires io.mokamint.node;
    requires io.mokamint.node.local;
	requires io.mokamint.application.api;
	requires io.mokamint.miner.local;
	requires io.mokamint.miner.remote;
	requires io.mokamint.node.remote;
	requires io.mokamint.plotter;
	requires io.hotmoka.cli;
	requires io.mokamint.application;
	requires io.mokamint.application.remote;
	requires io.mokamint.node.service;
	requires io.hotmoka.crypto;
	requires io.hotmoka.crypto.cli;
	requires io.hotmoka.exceptions;
	requires io.hotmoka.websockets.beans;
	requires io.hotmoka.websockets.client.api;
	requires com.google.gson;
    // this makes sun.misc.Unsafe accessible, so that Gson can instantiate classes without the no-args constructor
 	requires jdk.unsupported;
	requires java.logging;
}