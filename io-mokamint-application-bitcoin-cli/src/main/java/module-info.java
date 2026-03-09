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
 * This module implements the command-line tool for the Bitcoin Mokamint application.
 */
module io.mokamint.application.bitcoin.cli {
	exports io.mokamint.application.bitcoin.cli;
	exports io.mokamint.application.bitcoin.cli.requests;

	// needed to inject CLI options
	opens io.mokamint.application.bitcoin.cli.internal to info.picocli;
	opens io.mokamint.application.bitcoin.cli.internal.requests to info.picocli;

	requires io.mokamint.constants;
	requires io.mokamint.node.cli;
	requires io.mokamint.node;
	requires io.mokamint.node.remote.api;
	requires io.hotmoka.cli;
	requires io.hotmoka.crypto;

	// this makes sun.misc.Unsafe accessible, so that Gson can instantiate classes without the no-args constructor
 	//requires jdk.unsupported;
	requires java.logging;
}