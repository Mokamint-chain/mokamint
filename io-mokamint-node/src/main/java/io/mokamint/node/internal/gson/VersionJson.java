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

package io.mokamint.node.internal.gson;

import io.hotmoka.websockets.beans.api.InconsistentJsonException;
import io.hotmoka.websockets.beans.api.JsonRepresentation;
import io.mokamint.node.api.Version;
import io.mokamint.node.internal.VersionImpl;

/**
 * The JSON representation of a {@link Version}.
 */
public abstract class VersionJson implements JsonRepresentation<Version> {
	private final int major;
	private final int minor;
	private final int patch;

	protected VersionJson(Version version) {
		this.major = version.getMajor();
		this.minor = version.getMinor();
		this.patch = version.getPatch();
	}

	public int getMajor() {
		return major;
	}

	public int getMinor() {
		return minor;
	}

	public int getPatch() {
		return patch;
	}

	@Override
	public Version unmap() throws InconsistentJsonException {
		return new VersionImpl(this);
	}
}