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

import io.mokamint.node.api.Version;
import io.mokamint.node.internal.VersionImpl;
import io.mokamint.node.internal.json.VersionDecoder;
import io.mokamint.node.internal.json.VersionEncoder;
import io.mokamint.node.internal.json.VersionJson;

/**
 * Providers of versions.
 */
public abstract class Versions {

	private Versions() {}

	/**
	 * Yields a version object with the given components.
	 * 
	 * @param major the major version component
	 * @param minor the minor version component
	 * @param patch the patch version component
	 * @return the version object
	 */
	public static Version of(int major, int minor, int patch) {
		return new VersionImpl(major, minor, patch);
	}

	/**
	 * Yields a version object, corresponding to the version of Mokamint
	 * as reported the pom.xml file of the parent project.
	 * 
	 * @return the version object
	 * @throws IOException if the information in the pom.xml file cannot be accessed
	 */
	public static Version current() throws IOException {
		return new VersionImpl();
	}

	/**
	 * Gson encoder.
	 */
	public static class Encoder extends VersionEncoder {

		/**
		 * Creates a new encoder.
		 */
		public Encoder() {}
	}

	/**
	 * Gson decoder.
	 */
	public static class Decoder extends VersionDecoder {

		/**
		 * Creates a new decoder.
		 */
		public Decoder() {}
	}

    /**
     * Json representation.
     */
    public static class Json extends VersionJson {

    	/**
    	 * Creates the Json representation for the given version.
    	 * 
    	 * @param version the version
    	 */
    	public Json(Version version) {
    		super(version);
    	}
    }
}