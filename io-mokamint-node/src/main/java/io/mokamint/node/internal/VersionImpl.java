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

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;
import java.util.stream.Stream;

import io.hotmoka.websockets.beans.api.InconsistentJsonException;
import io.mokamint.node.api.Version;
import io.mokamint.node.internal.json.VersionJson;

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
		if (major < 0 || minor < 0 || patch < 0)
			throw new IllegalArgumentException("Version's components must be non-negative");

		this.major = major;
		this.minor = minor;
		this.patch = patch;
	}

	/**
	 * Creates a version object from the given JSON representation.
	 * 
	 * @param json the JSON representation
	 * @throws InconsistentJsonException if the JSON representation is inconsistent
	 */
	public VersionImpl(VersionJson json) throws InconsistentJsonException {
		int major = json.getMajor();
		int minor = json.getMinor();
		int patch = json.getPatch();

		if (major < 0 || minor < 0 || patch < 0)
			throw new InconsistentJsonException("Version's components must be non-negative");

		this.major = major;
		this.minor = minor;
		this.patch = patch;
	}

	/**
	 * Yields a new version object, corresponding to the version of Mokamint
	 * as reported the pom.xml file of the main project.
	 * 
	 * @throws IOException if the information of the pom.xml file cannot be accessed
	 */
	public VersionImpl() throws IOException {
		// reads the version from the property in the Maven pom.xml
		try (InputStream is = VersionImpl.class.getClassLoader().getResourceAsStream("maven.properties")) {
			var mavenProperties = new Properties();
			mavenProperties.load(is);
			// the period separates the version components, but we need an escaped escape sequence to refer to it in split
			int[] components = Stream.of(mavenProperties.getProperty("mokamint.version").split("\\.")).mapToInt(Integer::parseInt).toArray();
			if (components.length != 3)
				throw new IOException("The mokamint.version property of the maven.properties file should consist of three integer components, while I found " + components.length);

			major = components[0];
			minor = components[1];
			patch = components[2];

			if (major < 0 || minor < 0 || patch < 0)
				throw new IOException("Version's components must be non-negative");
		}
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
		return other instanceof Version otherAsVersion &&
			major == otherAsVersion.getMajor() &&
			minor == otherAsVersion.getMinor() &&
			patch == otherAsVersion.getPatch();
	}

	@Override
	public int hashCode() {
		return major + minor + patch;
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