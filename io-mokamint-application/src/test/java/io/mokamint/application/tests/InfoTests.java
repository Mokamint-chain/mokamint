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

package io.mokamint.application.tests;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import io.hotmoka.testing.AbstractLoggedTests;
import io.mokamint.application.Infos;

public class InfoTests extends AbstractLoggedTests {

	@Test
	@DisplayName("application information objects are correctly encoded into Json and decoded from Json")
	public void encodeDecodeWorks() throws Exception {
		var info1 = Infos.of("myname", "a very beautiful application");
		String encoded = new Infos.Encoder().encode(info1);
		var info2 = new Infos.Decoder().decode(encoded);
		assertEquals(info1, info2);
	}
}