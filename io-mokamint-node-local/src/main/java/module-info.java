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

module io.mokamint.node.local {
	exports io.mokamint.node.local;

	requires transitive io.mokamint.node.api;
	requires transitive io.mokamint.application.api;
	requires transitive io.hotmoka.spacemint.miner.api;
	requires io.hotmoka.spacemint.miner.local; // TODO: used online for main: remove at the end (also in pom.xml)
	requires io.hotmoka.spacemint.plotter; // TODO: used online for main: remove at the end (also in pom.xml)
	requires io.hotmoka.crypto; // TODO: used online for main: remove at the end (also in pom.xml) 
	requires java.logging;
}