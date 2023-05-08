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

module io.mokamint.plotter.tools {
	exports io.mokamint.plotter.tools;
	opens io.mokamint.plotter.tools to info.picocli; // for injecting CLI options
    opens io.mokamint.plotter.tools.internal to info.picocli; // for injecting CLI options

	requires io.mokamint.plotter;
	requires io.hotmoka.crypto;
	requires info.picocli;
	requires java.logging;
}