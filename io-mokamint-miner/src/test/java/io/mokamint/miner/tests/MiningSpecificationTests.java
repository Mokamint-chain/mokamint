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

package io.mokamint.miner.tests;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import io.hotmoka.testing.AbstractLoggedTests;
import io.mokamint.miner.MiningSpecifications;

public class MiningSpecificationTests extends AbstractLoggedTests {

	@Test
	@DisplayName("mining specifications are correctly encoded into Json and decoded from Json")
	public void encodeDecodeWorks() throws Exception {
		var miningSpecification1 = MiningSpecifications.of("octopus");
		String encoded = new MiningSpecifications.Encoder().encode(miningSpecification1);
		var miningSpecification2 = new MiningSpecifications.Decoder().decode(encoded);
		assertEquals(miningSpecification1, miningSpecification2);
	}
}