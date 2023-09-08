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

package io.mokamint.application.api;

/**
 * An application for the Mokamint blockchain.
 */
public interface Application {

	/**
	 * Called whenever a node receives a new deadline from one of its miners.
	 * The application can decide to accept or reject the deadline, on the
	 * basis of its prolog's extra bytes.
	 * 
	 * @param extra the extra, application-specific bytes of the prolog
	 * @return true if and only if the {@code extra} is valid according to this application
	 */
	boolean prologExtraIsValid(byte[] extra);
}