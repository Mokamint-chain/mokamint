/*
Copyright 2026 Fausto Spoto

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

package io.mokamint.node.cli.converters;

import java.io.IOException;
import java.nio.file.InvalidPathException;
import java.nio.file.Paths;

import io.hotmoka.crypto.Entropies;
import io.hotmoka.crypto.api.Entropy;
import picocli.CommandLine.ITypeConverter;

/**
 * A converter of a file path into the entropy contained therein.
 */
public class EntropyOptionConverter implements ITypeConverter<Entropy> {

	@Override
	public Entropy convert(String value) throws InvalidPathException, IOException {
		return Entropies.load(Paths.get(value));
	}
}