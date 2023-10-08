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
import java.security.InvalidKeyException;
import java.security.KeyPair;

import io.hotmoka.crypto.Base58;
import io.hotmoka.crypto.Entropies;
import io.hotmoka.crypto.api.SignatureAlgorithm;
import io.mokamint.node.tools.internal.SignatureOptionConverter;
import io.mokamint.tools.AbstractCommand;
import io.mokamint.tools.CommandException;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

@Command(name = "create", description = "Create a new key pair")
public class Create extends AbstractCommand {

	@Option(names = "--password", description = "the password that will be needed later to use the key pair", interactive = true, defaultValue = "")
    private String password;

	@Option(names = "--signature", description = "the signature to use for the key pair (ed25519, sha256dsa, qtesla1, qtesla3)",
			converter = SignatureOptionConverter.class, defaultValue = "ed25519")
	private SignatureAlgorithm signature;

	@Override
	protected void execute() throws CommandException {
		try {
			var entropy = Entropies.random();
			KeyPair keys = entropy.keys(password, signature);
			var publicKeyBase58 = Base58.encode(signature.encodingOf(keys.getPublic()));
			System.out.println("A new key pair has been created.");
			if (publicKeyBase58.length() > 100) {
				publicKeyBase58 = publicKeyBase58.substring(0, 100);
				System.out.println("Its public key Base58 is " + publicKeyBase58 + "...");
			}
			else
				System.out.println("Its public key Base58 is " + publicKeyBase58 + ".");

			var fileName = entropy.dump(publicKeyBase58);
			System.out.println("The key pair has been saved as \"" + fileName + "\".");
		}
		catch (IOException e) {
			throw new CommandException("The key pair could not be dumped into a file!", e);
		}
		catch (InvalidKeyException e) {
			throw new CommandException("The new key pair is invalid!", e);
		}
	}
}