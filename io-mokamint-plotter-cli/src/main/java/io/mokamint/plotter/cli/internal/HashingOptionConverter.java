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

package io.mokamint.plotter.cli.internal;

import java.security.NoSuchAlgorithmException;

import io.hotmoka.crypto.HashingAlgorithms;
import io.hotmoka.crypto.api.HashingAlgorithm;
import picocli.CommandLine.ITypeConverter;

/**
 * A converter of a string option into the hashing algorithm with that name.
 */
public class HashingOptionConverter implements ITypeConverter<HashingAlgorithm> {

	@Override
	public HashingAlgorithm convert(String value) throws NoSuchAlgorithmException {
		return HashingAlgorithms.of(value);
	}
}