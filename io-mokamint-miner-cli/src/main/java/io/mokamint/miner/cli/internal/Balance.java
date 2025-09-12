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

import java.math.BigInteger;
import java.net.URI;
import java.security.PublicKey;
import java.security.spec.InvalidKeySpecException;
import java.util.Optional;
import java.util.concurrent.TimeoutException;

import com.google.gson.Gson;

import io.hotmoka.cli.AbstractRpcCommand;
import io.hotmoka.cli.CommandException;
import io.hotmoka.crypto.Base58;
import io.hotmoka.crypto.api.SignatureAlgorithm;
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

			if (json()) {
				var answer = new Answer();
				answer.balance = maybeBalance.orElse(null);
				System.out.println(new Gson().toJsonTree(answer));
			}
			else
				System.out.println(maybeBalance.map(BigInteger::toString).orElse("not available"));
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