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

package io.mokamint.plotter.tools.internal;

import java.io.IOException;
import java.nio.file.Path;
import java.security.NoSuchAlgorithmException;

import io.hotmoka.crypto.Hex;
import io.mokamint.plotter.Plots;
import io.mokamint.tools.AbstractCommand;
import io.mokamint.tools.CommandException;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

@Command(name = "show",
	description = "Show a plot file.",
	showDefaultValues = true)
public class Show extends AbstractCommand {

	@Parameters(index = "0", description = "the path of the new plot file")
	private Path path;

	@Option(names = "--json", description = "print the output in JSON", defaultValue = "false")
	private boolean json;

	@Override
	protected void execute() {
		try (var plot = Plots.load(path)) {
			var prolog = plot.getProlog();
			System.out.println("* prolog:");
			System.out.println("  * chain identifier: " + prolog.getChainId());
			System.out.println("  * node's public key: " + prolog.getNodePublicKeyBase58());
			System.out.println("  * plot's public key: " + prolog.getPlotPublicKeyBase58());
			System.out.println("  * extra: " + Hex.toHexString(prolog.getExtra()));
			long start = plot.getStart();
			System.out.println("* nonces: [" + start + "," + (start + plot.getLength()) + ")");
			System.out.println("* hashing: " + plot.getHashing().getName());
		}
		catch (NoSuchAlgorithmException e) {
			throw new CommandException("The plot file uses an unknown hashing algorithm!", e);
		}
		catch (IOException e) {
			throw new CommandException("Cannot read the plot file!", e);
		}
	}
}