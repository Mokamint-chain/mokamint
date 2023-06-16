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

import io.mokamint.node.internal.ConsensusConfigImpl;

/**
 * The builder of a configuration object.
 * 
 * @param <T> the concrete type of the builder
 */
public abstract class AbstractConfigBuilder<T extends AbstractConfigBuilder<T>> extends ConsensusConfigImpl.AbstractBuilder<T> {

	protected AbstractConfigBuilder() throws NoSuchAlgorithmException {
	}

	/**
	 * Reads the properties of the given TOML file and sets them for
	 * the corresponding fields of this builder.
	 * 
	 * @param toml the file
	 * @throws NoSuchAlgorithmException if some hashing algorithm cannot be found
	 */
	protected AbstractConfigBuilder(Toml toml) throws NoSuchAlgorithmException {
		super(toml);
	}
}