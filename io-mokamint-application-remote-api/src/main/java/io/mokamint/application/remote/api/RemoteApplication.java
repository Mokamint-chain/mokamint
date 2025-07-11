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

package io.mokamint.application.remote.api;

import io.hotmoka.annotations.ThreadSafe;
import io.hotmoka.websockets.client.api.Remote;
import io.mokamint.application.api.Application;

/**
 * A remote node of a Mokamint blockchain, just seen as a closeable object.
 */
@ThreadSafe
public interface RemoteApplication extends Application, Remote {
}