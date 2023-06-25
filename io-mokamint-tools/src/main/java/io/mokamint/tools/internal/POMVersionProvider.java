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

package io.mokamint.tools.internal;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

import picocli.CommandLine.IVersionProvider;

/**
 * A picocli dynamic version provider, that reads the maven.properties file, where the
 * version of Mokamint is stored during the Maven build.
 */
public class POMVersionProvider implements IVersionProvider {

	@Override
	public String[] getVersion() throws IOException {
		try (InputStream is = POMVersionProvider.class.getClassLoader().getResourceAsStream("maven.properties")) {
			var mavenProperties = new Properties();
			mavenProperties.load(is);
			return new String[] { mavenProperties.getProperty("mokamint.version") };
		}
	}
}