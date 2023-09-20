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

package io.mokamint.node.local;

import java.io.FileNotFoundException;
import java.net.URISyntaxException;
import java.nio.file.Path;
import java.security.NoSuchAlgorithmException;

import io.mokamint.node.local.LocalNodeConfigImpl.LocalNodeConfigBuilderImpl;

/**
 * Providers of configuration object builders for local Mokamint nodes.
 */
public abstract class LocalNodeConfigBuilders {

	private LocalNodeConfigBuilders() {}

	/**
	 * Creates a builder containing default data.
	 * 
	 * @return the builder
	 * @throws NoSuchAlgorithmException if some hashing algorithm is not available
	 */
	public static LocalNodeConfigBuilder defaults() throws NoSuchAlgorithmException {
		return new LocalNodeConfigBuilderImpl();
	}

	/**
	 * Creates a builder from the given TOML configuration file.
	 * The resulting builder will contain the information in the file,
	 * and use defaults for the data not contained in the file.
	 * 
	 * @param path the path to the TOML file
	 * @return the builder
	 * @throws FileNotFoundException if {@code path} cannot be found
	 * @throws URISyntaxException if the file refers to a URI with a wrong syntax
	 * @throws NoSuchAlgorithmException if the file refers to a hashing algorithm that is not available
	 */
	public static LocalNodeConfigBuilder load(Path path) throws FileNotFoundException, NoSuchAlgorithmException, URISyntaxException {
		return new LocalNodeConfigBuilderImpl(path);
	}
}