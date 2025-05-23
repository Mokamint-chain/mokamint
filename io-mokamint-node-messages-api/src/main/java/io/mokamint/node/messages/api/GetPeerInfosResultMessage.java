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

package io.mokamint.node.messages.api;

import java.util.stream.Stream;

import io.hotmoka.annotations.Immutable;
import io.hotmoka.websockets.beans.api.ResultMessage;
import io.mokamint.node.api.PeerInfo;
import io.mokamint.node.api.PublicNode;

/**
 * The network message corresponding to the result of the {@link PublicNode#getPeerInfos()} method of a node.
 */
@Immutable
public interface GetPeerInfosResultMessage extends ResultMessage<Stream<PeerInfo>> {
}