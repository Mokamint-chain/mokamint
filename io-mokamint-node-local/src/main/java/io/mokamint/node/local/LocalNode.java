/*
Copyright 2021 Fausto Spoto

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

package io.mokamint.node.local;

import io.mokamint.node.api.Node;

/**
 * A local node of a Mokamint blockchain.
 */
public interface LocalNode extends Node {

	/**
	 * Closes the node.
	 * 
	 * @throws InterruptedException if the thread was interrupted while waiting
	 *                              for the executors to shut down
	 */
	@Override
	void close() throws InterruptedException;
}