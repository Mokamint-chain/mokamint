/*
Copyright 2025 Fausto Spoto

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

package io.mokamint.miner.api;

import io.hotmoka.annotations.Immutable;

/**
 * The specification of the kind of deadlines that are expected by a miner
 * (signature algorithms, public key of the node, chain id, etc).
 */
@Immutable
public interface MiningSpecification {

	/**
	 * Yields the chain id of the deadlines expected by a miner having this specification.
	 * 
	 * @return the chain id
	 */
	String getChainId();
}