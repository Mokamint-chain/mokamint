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

import io.hotmoka.annotations.ThreadSafe;
import io.hotmoka.closeables.api.OnCloseHandlersContainer;

/**
 * A node of a Mokamint blockchain.
 */
@ThreadSafe
public interface Node extends OnCloseHandlersContainer, AutoCloseable {

	/**
	 * Closes this node. After this closure, the methods of this node will throw
	 * a {@link ClosedNodeException}. A node cannot be reopened after being closed.
	 */
	@Override
	void close();
}