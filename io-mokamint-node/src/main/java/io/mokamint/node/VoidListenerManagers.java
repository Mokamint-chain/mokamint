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

package io.mokamint.node;

import io.mokamint.node.internal.VoidListenerManagerImpl;

/**
 * Provider of {@link VoidListenerManager}.
 */
public final class VoidListenerManagers {

	private VoidListenerManagers() {}

	/**
	 * Yields a new listener management object.
	 *
	 * @return the listener management object
	 */
	public static VoidListenerManager mk() {
		return new VoidListenerManagerImpl();
	}
}