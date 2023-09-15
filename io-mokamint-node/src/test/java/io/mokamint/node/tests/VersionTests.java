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

package io.mokamint.node.tests;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import io.mokamint.node.Versions;
import jakarta.websocket.DecodeException;
import jakarta.websocket.EncodeException;

public class VersionTests extends Tests {

	@Test
	@DisplayName("versions are correctly encoded into Json and decoded from Json")
	public void encodeDecodeWorksForVersion() throws EncodeException, DecodeException {
		var version1 = Versions.of(1, 2, 3);
		String encoded = new Versions.Encoder().encode(version1);
		var version2 = new Versions.Decoder().decode(encoded);
		assertEquals(version1, version2);
	}
}