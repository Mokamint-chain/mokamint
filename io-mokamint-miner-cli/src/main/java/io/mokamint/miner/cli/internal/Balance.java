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

package io.mokamint.miner.cli.internal;

import java.io.IOException;
import java.math.BigInteger;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.PublicKey;
import java.security.spec.InvalidKeySpecException;
import java.util.Optional;
import java.util.concurrent.TimeoutException;

import com.google.gson.Gson;

import io.hotmoka.cli.AbstractRpcCommand;
import io.hotmoka.cli.CommandException;
import io.hotmoka.crypto.Base58;
import io.hotmoka.crypto.api.SignatureAlgorithm;
import io.hotmoka.crypto.cli.converters.SignatureOptionConverter;
import io.mokamint.miner.api.ClosedMinerException;
import io.mokamint.miner.service.MinerServices;
import io.mokamint.miner.service.api.MinerService;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

@Command(name = "balance",
	description = "Show the balance of a public key in a remote miner.",
	showDefaultValues = true)
public class Balance extends AbstractRpcCommand<MinerService> {

	@Parameters(index = "0", description = "the Base58-encoded public key whose balance is required")
	private String publicKeyBase58;

	@Option(names = "--signature", description = "the signature algorithm for the public key (ed25519, sha256dsa, qtesla1, qtesla3)",
			converter = SignatureOptionConverter.class, defaultValue = "ed25519")
	private SignatureAlgorithm signature;

	@Option(names = "--uri", description = "the network URI where the API of the remote miner is published", defaultValue = "ws://localhost:8025")
	private URI uri;

	@Option(names = "--redirection", paramLabel = "<path>", description = "the path where the output must be redirected, if any; if missing, the output is printed to the standard output")
	private Path redirection;

	@Option(names = "--json", description = "print the output in JSON", defaultValue = "false")
	private boolean json;

	protected Balance() {
	}

	@Override
	protected void execute() throws CommandException {
		execute(MinerServices::of, this::body, uri);
	}

	private void body(MinerService service) throws TimeoutException, InterruptedException, CommandException {
		PublicKey publicKey;

		try {
			publicKey = signature.publicKeyFromEncoding(Base58.fromBase58String(publicKeyBase58, CommandException::new));
		}
		catch (InvalidKeySpecException e) {
			throw new CommandException("The public key is invalid for signature " + signature);
		}

		try {
			Optional<BigInteger> maybeBalance = service.getBalance(signature, publicKey);

			String result;
			if (json) {
				var answer = new Answer();
				answer.balance = maybeBalance.orElse(null);
				result = new Gson().toJsonTree(answer).toString() + "\n";
			}
			else
				result = maybeBalance.map(BigInteger::toString).orElse("not available") + "\n";

			if (redirection == null)
				System.out.print(result);
			else {
				try {
					Files.writeString(redirection, result);
				}
				catch (IOException e) {
					throw new CommandException("Could not write the output into \"" + redirection + "\": " + e.getMessage());
				}
			}
		}
		catch (ClosedMinerException e) {
			throw new CommandException("The mining remote has been closed: " + e.getMessage());
		}
	}

	private static class Answer {
		@SuppressWarnings("unused")
		private BigInteger balance;
	}
}