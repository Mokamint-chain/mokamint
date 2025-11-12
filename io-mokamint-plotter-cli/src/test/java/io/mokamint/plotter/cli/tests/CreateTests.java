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

package io.mokamint.plotter.cli.tests;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.nio.file.Path;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import io.hotmoka.crypto.HashingAlgorithms;
import io.hotmoka.crypto.SignatureAlgorithms;
import io.hotmoka.crypto.cli.keys.KeysShowOutputs;
import io.hotmoka.testing.AbstractLoggedTests;
import io.mokamint.plotter.cli.MokamintPlotter;
import io.mokamint.plotter.cli.ShowOutputs;
import io.mokamint.plotter.cli.api.ShowOutput;

/**
 * Tests for the {@code mokamint-plotter create} command.
 */
public class CreateTests extends AbstractLoggedTests {

	@Test
	@DisplayName("[mokamint-plotter create] information about a new plot file is correctly reported")
	public void createPlotThenShowWorksCorrectly(@TempDir Path dir) throws Exception {
		var expectedSignatureOfNode = SignatureAlgorithms.sha256dsa();
		var nodeKeyPassword = "nodepassword";
		var nodeKeyFileName = "node.pem";
		var expectedPublicKeyNodeBase58 = KeysShowOutputs.from(MokamintPlotter.keysCreate("--output-dir=" + dir + " --name=" + nodeKeyFileName + " --signature=" + expectedSignatureOfNode + " --password=" + nodeKeyPassword + " --json")).getPublicKeyBase58();

		var expectedSignatureOfPlot = SignatureAlgorithms.ed25519();
		var deadlinesKeyPassword = "deadlinespassword";
		var deadlinesKeyFileName = "deadlines.pem";
		var expectedPublicKeyDeadlinesBase58 = KeysShowOutputs.from(MokamintPlotter.keysCreate("--output-dir=" + dir + " --name=" + deadlinesKeyFileName + " --signature=" + expectedSignatureOfPlot + " --password=" + deadlinesKeyPassword + " --json")).getPublicKeyBase58();

		var plotFile = dir.resolve("file.plot");
		var expectedStart = 100L;
		var expectedLength = 50L;
		var expectedChainId = "octopus";
		var expectedHashing = HashingAlgorithms.shabal256();
		MokamintPlotter.create(plotFile + " " + expectedStart + " " + expectedLength + " " + expectedChainId + " " + expectedPublicKeyNodeBase58 + " " + expectedPublicKeyDeadlinesBase58
			+ " --signature-of-node " + expectedSignatureOfNode + " --signature-of-plot " + expectedSignatureOfPlot + " --hashing " + expectedHashing + " --json");

		ShowOutput actual = ShowOutputs.from(MokamintPlotter.show(plotFile + " --json"));
		var prolog = actual.getProlog();
		assertEquals(expectedPublicKeyNodeBase58, prolog.getPublicKeyForSigningBlocksBase58());
		assertEquals(expectedPublicKeyDeadlinesBase58, prolog.getPublicKeyForSigningDeadlinesBase58());
		assertEquals(expectedSignatureOfNode, prolog.getSignatureForBlocks());
		assertEquals(expectedSignatureOfPlot, prolog.getSignatureForDeadlines());
		assertEquals(expectedChainId, prolog.getChainId());
		assertEquals(expectedHashing, actual.getHashing());
		assertEquals(expectedStart, actual.getStart());
		assertEquals(expectedLength, actual.getLength());
	}
}