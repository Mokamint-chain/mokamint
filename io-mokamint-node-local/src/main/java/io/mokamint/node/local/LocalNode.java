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

package io.mokamint.node.local;

import io.mokamint.node.PublicNodeInternals;
import io.mokamint.node.RestrictedNodeInternals;
import io.mokamint.node.api.AutoCloseableNode;
import io.mokamint.node.api.FullNode;
import io.mokamint.node.api.WhisperingNode;

/**
 * A local node of a Mokamint blockchain.
 */
public interface LocalNode extends FullNode, PublicNodeInternals, RestrictedNodeInternals, WhisperingNode, AutoCloseableNode {
}