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

package io.mokamint.node.internal;

import io.mokamint.node.api.Version;

/**
 * Implementation of the version of a Mokamint node.
 */
public class VersionImpl implements Version {

	/**
	 * The major version component.
	 */
	private final int major;

	/**
	 * The minor version component.
	 */
	private final int minor;

	/**
	 * The patch version component.
	 */
	private final int patch;

	/**
	 * Yields a new version object.
	 * 
	 * @param major the major version component
	 * @param minor the minor version component
	 * @param patch the patch version component
	 */
	public VersionImpl(int major, int minor, int patch) {
		this.major = major;
		this.minor = minor;
		this.patch = patch;
	}

	@Override
	public int getMajor() {
		return major;
	}

	@Override
	public int getMinor() {
		return minor;
	}

	@Override
	public int getPatch() {
		return patch;
	}

	@Override
	public boolean equals(Object other) {
		if (other instanceof Version) {
			var otherAsVersion = (Version) other;
			return major == otherAsVersion.getMajor() && minor == otherAsVersion.getMinor() && patch == otherAsVersion.getPatch();
		}
		else
			return false;
	}

	@Override
	public String toString() {
		return major + "." + minor + "." + patch;
	}

	@Override
	public boolean canWorkWith(Version other) {
		return major == other.getMajor() && minor == other.getMinor();
	}

}