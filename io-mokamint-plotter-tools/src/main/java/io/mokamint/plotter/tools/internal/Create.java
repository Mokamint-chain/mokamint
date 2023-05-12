/*
Copyright 2021 Fausto Spoto

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

package io.mokamint.plotter.tools.internal;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.NoSuchAlgorithmException;

import io.hotmoka.crypto.HashingAlgorithms;
import io.mokamint.plotter.Plots;
import io.mokamint.tools.AbstractCommand;
import picocli.CommandLine.Command;
import picocli.CommandLine.Help.Ansi;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

@Command(name = "create",
	description = "Create a new plot file.",
	showDefaultValues = true)
public class Create extends AbstractCommand {

	@Parameters(index = "0", description = "the path of the new plot file")
	private Path path;

	@Parameters(index = "1", description = "the initial nonce number")
	private long start;

	@Parameters(index = "2", description = "the amount of nonces")
	private long length;

	@Option(names = "--hashing", description = "the name of the hashing algorithm", defaultValue = "shabal256")
	private String hashing;

	@Override
	protected void execute() throws IOException, NoSuchAlgorithmException {
		Files.deleteIfExists(path);

		var prolog = new byte[] { 11, 13, 24, 88 };
		var algorithm = HashingAlgorithms.mk(hashing, (byte[] bytes) -> bytes);

		try (var plot = Plots.create(path, prolog, start, length, algorithm, this::onNewPercent)) {
		}

		System.out.println();
	}

	private void onNewPercent(int percent) {
		if (percent % 5 == 0)
			System.out.print(Ansi.AUTO.string("@|bold,red " + percent + "%|@ "));
		else
			System.out.print(percent + "% ");
	}
}