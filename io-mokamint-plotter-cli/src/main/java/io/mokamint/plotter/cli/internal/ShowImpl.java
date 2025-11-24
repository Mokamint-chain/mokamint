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

import java.io.IOException;
import java.nio.file.Path;
import java.security.NoSuchAlgorithmException;

import io.hotmoka.annotations.Immutable;
import io.hotmoka.cli.AbstractCommandWithJsonOutput;
import io.hotmoka.cli.CommandException;
import io.hotmoka.crypto.HashingAlgorithms;
import io.hotmoka.crypto.Hex;
import io.hotmoka.crypto.api.HashingAlgorithm;
import io.hotmoka.exceptions.Objects;
import io.hotmoka.websockets.beans.api.InconsistentJsonException;
import io.mokamint.nonce.api.Prolog;
import io.mokamint.plotter.Plots;
import io.mokamint.plotter.api.Plot;
import io.mokamint.plotter.cli.ShowOutputs;
import io.mokamint.plotter.cli.api.ChainInfoOutput;
import io.mokamint.plotter.cli.internal.json.ShowOutputJson;
import picocli.CommandLine.Parameters;

public class ShowImpl extends AbstractCommandWithJsonOutput {

	@Parameters(index = "0", description = "the path of the plot file")
	private Path path;

	@Override
	protected void execute() throws CommandException {
		try (var plot = Plots.load(path)) {
			report(new Output(plot), ShowOutputs.Encoder::new);
		}
		catch (NoSuchAlgorithmException e) {
			throw new CommandException("The plot file uses an unknown cryptographic algorithm!", e);
		}
		catch (IOException e) {
			throw new CommandException("Cannot access the plot file!", e);
		}
	}

	/**
	 * The output of this command.
	 */
	@Immutable
	public static class Output implements ChainInfoOutput {
		private final Prolog prolog;
		private final long start;
		private final long length;
		private final HashingAlgorithm hashing;

		/**
		 * Builds the output of the command from its JSON representation.
		 * 
		 * @param json the JSON representation
		 * @throws InconsistentJsonException if {@code json} is inconsistent
		 * @throws NoSuchAlgorithmException if the JSON refers to an unavailable cryptographic algorithm
		 */
		public Output(ShowOutputJson json) throws InconsistentJsonException, NoSuchAlgorithmException {
			this.prolog = Objects.requireNonNull(json.getProlog(), "prolog cannot be null", InconsistentJsonException::new).unmap();
			this.start = json.getStart();
			this.length = json.getLength();
			this.hashing = HashingAlgorithms.of(Objects.requireNonNull(json.getHashing(), "hashing cannot be null", InconsistentJsonException::new));
		}

		private Output(Plot plot) {
			this.prolog = plot.getProlog();
			this.start = plot.getStart();
			this.length = plot.getLength();
			this.hashing = plot.getHashing();
		}

		@Override
		public Prolog getProlog() {
			return prolog;
		}

		@Override
		public long getStart() {
			return start;
		}

		@Override
		public long getLength() {
			return length;
		}

		@Override
		public HashingAlgorithm getHashing() {
			return hashing;
		}

		@Override
		public String toString() {
			var sb = new StringBuilder();

			sb.append("* prolog:\n");
			sb.append("  * chain identifier: " + prolog.getChainId());
			sb.append("\n");
			sb.append("  * nodes's public key for signing blocks: " + prolog.getPublicKeyForSigningBlocksBase58() + " (" + prolog.getSignatureForBlocks() + ", base58)");
			sb.append("\n");
			sb.append("  * nodes's public key for signing deadlines: " + prolog.getPublicKeyForSigningDeadlinesBase58() + " (" + prolog.getSignatureForDeadlines() + ", base58)");
			sb.append("\n");
			sb.append("  * extra: " + Hex.toHexString(prolog.getExtra()));
			sb.append("\n");
			sb.append("* nonces: [" + start + "," + (start + length) + ")\n");
			sb.append("* hashing: " + hashing);
			sb.append("\n");

			return sb.toString();
		}
	}
}