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

import io.hotmoka.websockets.beans.api.JsonRepresentation;
import io.mokamint.node.Versions;
import io.mokamint.node.api.Version;

/**
 * The JSON representation of a {@link Version}.
 */
public abstract class VersionJson implements JsonRepresentation<Version> {
	private int major;
	private int minor;
	private int patch;

	protected VersionJson(Version version) {
		this.major = version.getMajor();
		this.minor = version.getMinor();
		this.patch = version.getPatch();
	}

	@Override
	public Version unmap() {
		return Versions.of(major, minor, patch);
	}
}