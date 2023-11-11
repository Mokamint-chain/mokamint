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

package io.mokamint.node.api;

import java.util.function.Predicate;

/**
 * An object that whispers peers, transactions and blocks.
 */
public interface Whisperer {

	/**
	 * Whisper the given object.
	 * 
	 * @param whispered the object to whisper
	 * @param seen a predicate telling if a whisperer has already whispered the
	 *             {@code whispered} object. This is used in order to avoid infinite recursion
	 *             if whisperers form a cycle inside the same machine (this does not account
	 *             for network connections among whisperers)
	 * @param description a description of what is being whispered, for logging
	 */
	void whisper(Whispered whispered, Predicate<Whisperer> seen, String description);
}