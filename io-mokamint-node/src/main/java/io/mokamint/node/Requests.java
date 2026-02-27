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

import java.io.IOException;

import io.hotmoka.marshalling.api.UnmarshallingContext;
import io.mokamint.node.api.Request;
import io.mokamint.node.internal.RequestImpl;

/**
 * Providers of request objects.
 */
public abstract class Requests {

	private Requests() {}

	/**
	 * Yields a new request object.
	 * 
	 * @param bytes the bytes of the request
	 * @return the request object
	 */
	public static Request of(byte[] bytes) {
		return new RequestImpl(bytes);
	}

	/**
	 * Unmarshals a request from the given context.
	 * 
	 * @param context the context
	 * @return the request
	 * @throws IOException if the request cannot be unmarshalled
	 */
	public static Request from(UnmarshallingContext context) throws IOException {
		return new RequestImpl(context);
	}
}