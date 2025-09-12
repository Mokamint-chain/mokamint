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
package io.mokamint.application;

import io.hotmoka.websockets.beans.MappedDecoder;
import io.hotmoka.websockets.beans.MappedEncoder;
import io.mokamint.application.api.Info;
import io.mokamint.application.internal.InfoImpl;
import io.mokamint.application.internal.json.InfoJson;

/**
 * Providers of application information objects.
 */
public abstract class Infos {

	private Infos() {}

	/**
	 * Yields the information about an application.
	 * 
	 * @param name the name of the application
	 * @param description a description of the application
	 * @return the information object
	 */
	public static Info of(String name, String description) {
		return new InfoImpl(name, description);
	}

	/**
	 * JSON representation.
	 */
	public static class Json extends InfoJson {
	
		/**
		 * Creates the JSON representation for the given application information.
		 * 
		 * @param info the application information
		 */
		public Json(Info info) {
			super(info);
		}
	}

	/**
	 * JSON encoder.
	 */
	public static class Encoder extends MappedEncoder<Info, Json> {

		/**
		 * Creates a new encoder.
		 */
		public Encoder() {
			super(Json::new);
		}
	}

	/**
	 * JSON decoder.
	 */
	public static class Decoder extends MappedDecoder<Info, Json> {

		/**
		 * Creates a new decoder.
		 */
		public Decoder() {
			super(Json.class);
		}
	}
}