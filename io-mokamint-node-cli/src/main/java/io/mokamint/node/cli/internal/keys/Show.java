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

package io.mokamint.node.cli.internal.keys;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.InvalidKeyException;
import java.security.KeyPair;
import java.util.Arrays;

import com.google.gson.Gson;

import io.hotmoka.cli.AbstractCommand;
import io.hotmoka.cli.CommandException;
import io.hotmoka.crypto.Base58;
import io.hotmoka.crypto.Entropies;
import io.hotmoka.crypto.api.SignatureAlgorithm;
import io.mokamint.node.cli.internal.SignatureOptionConverter;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

@Command(name = "show", description = "Show a key pair")
public class Show extends AbstractCommand {

	@Parameters(index = "0", description = "the file containing the key pair (without the trailing .pem)")
	private Path path;

	@Option(names = "--password", description = "the password of the key pair", interactive = true, defaultValue = "")
    private char[] password;

	@Option(names = "--signature", description = "the signature to use for the key pair (ed25519, sha256dsa, qtesla1, qtesla3)",
			converter = SignatureOptionConverter.class, defaultValue = "ed25519")
	private SignatureAlgorithm signature;

	@Option(names = "--redirection", paramLabel = "<path>", description = "the path where the output must be redirected, if any; if missing, the output is printed to the standard output")
	private Path redirection;

	@Option(names = "--json", description = "print the output in JSON", defaultValue = "false")
	private boolean json;

	@Option(names = "--full", description = "report keys in full, also if they are very long", defaultValue = "false")
	private boolean full;

	@Override
	protected void execute() throws CommandException {
		String passwordAsString;
		try {
			var entropy = Entropies.load(path);
			passwordAsString = new String(password);
			KeyPair keys = entropy.keys(passwordAsString, signature);

			var publicKeyBase58 = Base58.toBase58String(signature.encodingOf(keys.getPublic()));
			if (!full && publicKeyBase58.length() > 100)
				publicKeyBase58 = publicKeyBase58.substring(0, 100) + "...";
			
			var privateKeyBase58 = Base58.toBase58String(signature.encodingOf(keys.getPrivate()));
			if (!full && privateKeyBase58.length() > 100)
				privateKeyBase58 = privateKeyBase58.substring(0, 100) + "...";

			String result;

			if (json) {
				var answer = new Answer();
				answer.publicKeyBase58 = publicKeyBase58;
				answer.privateKeyBase58 = privateKeyBase58;
				result = new Gson().toJsonTree(answer) + "\n";
			}
			else {
				result = " Public key: " + publicKeyBase58 + " (" + signature + ", base58)\n";
				result += "Private key: " + privateKeyBase58 + " (" + signature + ", base58)\n";
			}

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
		catch (IOException e) {
			throw new CommandException("The key pair could not be loaded from file " + path + "!", e);
		}
		catch (InvalidKeyException e) {
			throw new CommandException("The key pair is invalid!", e);
		}
		finally {
			passwordAsString = null;
			Arrays.fill(password, ' ');
		}
	}

	private static class Answer {
		@SuppressWarnings("unused")
		private String publicKeyBase58;

		@SuppressWarnings("unused")
		private String privateKeyBase58;
	}
}