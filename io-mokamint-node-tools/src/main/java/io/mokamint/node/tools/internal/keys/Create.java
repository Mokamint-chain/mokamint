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

package io.mokamint.node.tools.internal.keys;

import java.io.IOException;
import java.nio.file.Path;
import java.security.InvalidKeyException;
import java.security.KeyPair;
import java.util.Arrays;

import com.google.gson.Gson;

import io.hotmoka.crypto.Base58;
import io.hotmoka.crypto.Entropies;
import io.hotmoka.crypto.api.SignatureAlgorithm;
import io.mokamint.node.tools.internal.SignatureOptionConverter;
import io.mokamint.tools.AbstractCommand;
import io.mokamint.tools.CommandException;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

@Command(name = "create", description = "Create a new key pair")
public class Create extends AbstractCommand {

	@Parameters(index = "0", description = "the file where the key pair will be stored")
	private Path path;

	@Option(names = "--password", description = "the password that will be needed later to use the key pair", interactive = true, defaultValue = "")
    private char[] password;

	@Option(names = "--signature", description = "the signature algorithm for the key pair (ed25519, sha256dsa, qtesla1, qtesla3)",
			converter = SignatureOptionConverter.class, defaultValue = "ed25519")
	private SignatureAlgorithm signature;

	@Option(names = "--json", description = "print the output in JSON", defaultValue = "false")
	private boolean json;

	@Override
	protected void execute() throws CommandException {
		String passwordAsString;
		try {
			var entropy = Entropies.random();
			passwordAsString = new String(password);
			KeyPair keys = entropy.keys(passwordAsString, signature);
			var publicKeyBase58 = Base58.encode(signature.encodingOf(keys.getPublic()));
			entropy.dump(path.toString().substring(0, path.toString().length() - ".pem".length())); // TODO: just path after moving to Hotmoka 1.4.0

			if (json) {
				var answer = new Answer();
				answer.publicKeyBase58 = publicKeyBase58;
				answer.fileName = path.toString();
				System.out.println(new Gson().toJsonTree(answer));
			}
			else {
				System.out.println("A new key pair has been created and saved as \"" + path + "\".");
				if (publicKeyBase58.length() > 100)
					publicKeyBase58 = publicKeyBase58.substring(0, 100) + "...";

				System.out.println("Its public key is " + publicKeyBase58 + " (" + signature + ", base58).");
			}
		}
		catch (IOException e) {
			throw new CommandException("The key pair could not be dumped into a file!", e);
		}
		catch (InvalidKeyException e) {
			throw new CommandException("The new key pair is invalid!", e);
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
		private String fileName;
	}
}