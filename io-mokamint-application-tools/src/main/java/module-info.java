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
 * This module implements a command-line tool for working with Mokamint applications.
 */
module io.mokamint.application.tools {
	exports io.mokamint.application.tools;
    opens io.mokamint.application.tools.internal to info.picocli; // for injecting CLI options
    
    // TODO: remove at the end
    provides io.mokamint.application.api.Application with io.mokamint.application.tools.internal.List.App1, io.mokamint.application.tools.internal.List.App42;
    uses io.mokamint.application.api.Application;

    requires io.mokamint.application.api;
    //requires io.mokamint.application.service;
    requires io.mokamint.tools;
	requires java.logging;
}