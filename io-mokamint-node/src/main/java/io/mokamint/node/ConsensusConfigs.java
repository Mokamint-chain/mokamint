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

import java.io.FileNotFoundException;
import java.nio.file.Path;
import java.security.NoSuchAlgorithmException;

import io.mokamint.node.api.ConsensusConfig;
import io.mokamint.node.internal.ConsensusConfigImpl;
import io.mokamint.node.internal.gson.ConsensusConfigDecoder;
import io.mokamint.node.internal.gson.ConsensusConfigEncoder;
import io.mokamint.node.internal.gson.ConsensusConfigJson;

/**
 * Providers of consensus configurations.
 */
public abstract class ConsensusConfigs {

	private ConsensusConfigs() {}

	/**
	 * The builder of consensus configurations, according to the builder pattern.
	 */
	private static class ConsensusConfigBuilder extends AbstractConfigBuilder<ConsensusConfigBuilder> {

		private ConsensusConfigBuilder() throws NoSuchAlgorithmException {
		}

		private ConsensusConfigBuilder(Path path) throws NoSuchAlgorithmException, FileNotFoundException {
			super(readToml(path));
		}

		@Override
		public ConsensusConfig build() {
			return new ConsensusConfigImpl(this);
		}

		@Override
		protected ConsensusConfigBuilder getThis() {
			return this;
		}
	}

	/**
	 * Creates a builder containing default data.
	 * 
	 * @return the builder
	 * @throws NoSuchAlgorithmException if some hashing algorithm used in the default configuration is not available
	 */
	public static AbstractConfigBuilder<?> defaults() throws NoSuchAlgorithmException {
		return new ConsensusConfigBuilder();
	}

	/**
	 * Creates a builder from the given TOML configuration file.
	 * The resulting builder will contain the information in the file,
	 * and use defaults for the data not contained in the file.
	 * 
	 * @param path the path to the TOML file
	 * @return the builder
	 * @throws FileNotFoundException if {@code path} cannot be found
	 * @throws NoSuchAlgorithmException if the configuration file refers to some non-available hashing algorithm
	 */
	public static AbstractConfigBuilder<?> load(Path path) throws NoSuchAlgorithmException, FileNotFoundException {
		return new ConsensusConfigBuilder(path);
	}

	/**
	 * Gson encoder.
	 */
	public static class Encoder extends ConsensusConfigEncoder {

		/**
		 * Creates a new encoder.
		 */
		public Encoder() {}
	}

	/**
	 * Gson decoder.
	 */
	public static class Decoder extends ConsensusConfigDecoder {

		/**
		 * Creates a new decoder.
		 */
		public Decoder() {}
	}

    /**
     * Json representation.
     */
    public static class Json extends ConsensusConfigJson {

    	/**
    	 * Creates the Json representation for the given configuration.
    	 * 
    	 * @param config the configuration
    	 */
    	public Json(ConsensusConfig config) {
    		super(config);
    	}
    }
}