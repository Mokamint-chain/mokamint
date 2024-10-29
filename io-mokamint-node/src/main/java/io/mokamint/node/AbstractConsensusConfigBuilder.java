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

import java.security.NoSuchAlgorithmException;

import com.moandjiezana.toml.Toml;

import io.mokamint.node.api.ConsensusConfig;
import io.mokamint.node.api.ConsensusConfigBuilder;
import io.mokamint.node.internal.ConsensusConfigImpl;

/**
 * The builder of a configuration object.
 * 
 * @param <C> the concrete type of the configuration
 * @param <B> the concrete type of the builder
 */
public abstract class AbstractConsensusConfigBuilder<C extends ConsensusConfig<C,B>, B extends ConsensusConfigBuilder<C,B>> extends ConsensusConfigImpl.ConsensusConfigBuilderImpl<C,B> {

	/**
	 * Creates the builder.
	 * 
	 * @throws NoSuchAlgorithmException if the configuration refers to some unknown hashing algorithm
	 */
	protected AbstractConsensusConfigBuilder() throws NoSuchAlgorithmException {}

	/**
	 * Reads the properties of the given TOML file and sets them for
	 * the corresponding fields of this builder.
	 * 
	 * @param toml the file
	 * @throws NoSuchAlgorithmException if the toml file refers to some unknown hashing algorithm
	 */
	protected AbstractConsensusConfigBuilder(Toml toml) throws NoSuchAlgorithmException {
		super(toml);
	}

	/**
	 * Creates a builder with properties initialized to those of the given configuration object.
	 * 
	 * @param config the configuration object
	 */
	protected AbstractConsensusConfigBuilder(ConsensusConfig<C,B> config) {
		super(config);
	}
}