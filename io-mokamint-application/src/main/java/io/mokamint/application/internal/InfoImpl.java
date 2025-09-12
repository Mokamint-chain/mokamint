/*
Copyright 2025 Fausto Spoto

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

package io.mokamint.application.internal;

import io.hotmoka.annotations.Immutable;
import io.hotmoka.exceptions.ExceptionSupplierFromMessage;
import io.hotmoka.exceptions.Objects;
import io.hotmoka.websockets.beans.api.InconsistentJsonException;
import io.mokamint.application.api.Info;
import io.mokamint.application.internal.json.InfoJson;

/**
 * An implementation of the information about a Mokamint application.
 */
@Immutable
public class InfoImpl implements Info {

	/**
	 * The name of the application.
	 */
	private final String name;

	/**
	 * A description of the application.
	 */
	private final String description;

	/**
	 * Creates an entry of the mempool of a Mokamint node.
	 * 
	 * @param hash the hash of the transaction in the entry
	 * @param priority the priority of the transaction in the entry
	 */
	public InfoImpl(String name, String description) {
		this(name, description, IllegalArgumentException::new);
	}

	/**
	 * Builds the information.
	 * 
	 * @param <E> the type of the exception thrown if some argument is illegal
	 * @param name the name of the application
	 * @param description the description of the application
	 * @throws E if some argument is illegal
	 */
	private <E extends Exception> InfoImpl(String name, String description, ExceptionSupplierFromMessage<? extends E> onIllegalArgs) throws E {
		this.name = Objects.requireNonNull(name, onIllegalArgs);
		this.description = Objects.requireNonNull(description, onIllegalArgs);
	}

	/**
	 * Creates the information from the given JSON representation.
	 * 
	 * @param json the JSON representation
	 * @throws InconsistentJsonException if the JSON representation is inconsistent
	 */
	public InfoImpl(InfoJson json) throws InconsistentJsonException {
		this(json.getName(), json.getDescription(), InconsistentJsonException::new);
	}

	@Override
	public String getName() {
		return name;
	}

	@Override
	public String getDescription() {
		return description;
	}

	@Override
	public boolean equals(Object other) {
		return other instanceof InfoImpl ii && name.equals(ii.name)&& description.equals(ii.getDescription());
	}

	@Override
	public int hashCode() {
		return name.hashCode() ^ description.hashCode();
	}

	@Override
	public String toString() {
		return name + ": " + description;
	}
}