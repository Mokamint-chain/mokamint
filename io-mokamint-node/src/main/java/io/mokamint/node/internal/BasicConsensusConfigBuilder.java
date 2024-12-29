/*
Copyright 2024 Fausto Spoto

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

import java.io.FileNotFoundException;
import java.nio.file.Path;
import java.security.NoSuchAlgorithmException;

import io.hotmoka.websockets.beans.api.InconsistentJsonException;
import io.mokamint.node.internal.gson.BasicConsensusConfigJson;

/**
 * The builder of consensus configurations, according to the builder pattern.
 */
public class BasicConsensusConfigBuilder extends ConsensusConfigImpl.ConsensusConfigBuilderImpl<BasicConsensusConfig, BasicConsensusConfigBuilder> {

	/**
	 * Creates a builder of configurations, initialized to default values.
	 * 
	 * @throws NoSuchAlgorithmException if some cryptographic algorithm is not available.
	 */
	public BasicConsensusConfigBuilder() throws NoSuchAlgorithmException {}

	/**
	 * Creates a builder of configurations, initialized from the given file and
	 * to default values whenever the file does not report a value for some option.
	 * 
	 * @param path the path to the file
	 * @throws NoSuchAlgorithmException if some cryptographic algorithm is not available.
	 * @throws FileNotFoundException if {@code path} cannot be found
	 */
	public BasicConsensusConfigBuilder(Path path) throws NoSuchAlgorithmException, FileNotFoundException {
		super(readToml(path));
	}

	/**
	 * Creates a builder of configurations from the given configuration.
	 * 
	 * @param config the configuration
	 */
	public BasicConsensusConfigBuilder(BasicConsensusConfig config) {
		super(config);
	}

	/**
	 * Creates a consensus builder from the given JSON representation.
	 * 
	 * @param json the JSON representation
	 * @throws InconsistentJsonException if {@code json} is inconsistent
	 * @throws NoSuchAlgorithmException if {@code json} refers to a non-available cryptographic algorithm
	 */
	public BasicConsensusConfigBuilder(BasicConsensusConfigJson json) throws InconsistentJsonException, NoSuchAlgorithmException {
		super(json);
	}

	@Override
	public BasicConsensusConfig build() {
		return new BasicConsensusConfig(this);
	}

	@Override
	protected BasicConsensusConfigBuilder getThis() {
		return this;
	}
}