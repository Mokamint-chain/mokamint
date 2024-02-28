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

package io.mokamint.application;

import java.util.ServiceLoader.Provider;
import java.util.stream.Stream;

import io.mokamint.application.api.Application;
import io.mokamint.application.internal.ApplicationLoader;

/**
 * Provider of applications.
 */
public abstract class Applications {

	private Applications() {}

	/**
	 * Loads from the module path the application with the given name.
	 * 
	 * @return the application
	 * @throws IllegalArgumentException if there is no application with the given name
	 *                                  or if there is more than one
	 */
	public static Application load(String name) {
		return ApplicationLoader.load(name);
	}

	/**
	 * Yields the providers of the applications accessible from the module path.
	 * 
	 * @return the providers
	 */
	public static Stream<Provider<Application>> available() {
		return ApplicationLoader.available();
	}
}