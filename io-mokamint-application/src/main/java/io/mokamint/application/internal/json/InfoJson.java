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

package io.mokamint.application.internal.json;

import io.hotmoka.websockets.beans.api.InconsistentJsonException;
import io.hotmoka.websockets.beans.api.JsonRepresentation;
import io.mokamint.application.api.Info;
import io.mokamint.application.internal.InfoImpl;

/**
 * The JSON representation of a {@link Info}.
 */
public abstract class InfoJson implements JsonRepresentation<Info> {
	private final String name;
	private final String description;

	protected InfoJson(Info info) {
		this.name = info.getName();
		this.description = info.getDescription();
	}

	public String getName() {
		return name;
	}

	public String getDescription() {
		return description;
	}

	@Override
	public Info unmap() throws InconsistentJsonException {
		return new InfoImpl(this);
	}
}