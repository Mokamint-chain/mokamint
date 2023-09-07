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
import java.security.NoSuchAlgorithmException;
import java.util.function.Function;

import io.hotmoka.crypto.Base58;
import io.hotmoka.crypto.Entropies;
import io.hotmoka.crypto.SignatureAlgorithms;
import io.mokamint.tools.AbstractCommand;
import io.mokamint.tools.CommandException;
import picocli.CommandLine.Command;

@Command(name = "create", description = "Create a new key pair")
public class Create extends AbstractCommand {

	@Override
	protected void execute() {
		try {
			var signatureAlgorithmOfNewAccount = SignatureAlgorithms.ed25519(Function.identity());
			var entropy = Entropies.random();
			KeyPair keys = entropy.keys("", signatureAlgorithmOfNewAccount);
			byte[] publicKeyBytes = signatureAlgorithmOfNewAccount.encodingOf(keys.getPublic());
			var publicKeyBase58 = Base58.encode(publicKeyBytes);
			System.out.println("A new key pair has been created.");
			System.out.println("Its public key Base58 is " + publicKeyBase58 + ".");
			Path fileName = entropy.dump(publicKeyBase58);
			System.out.println("The key pair has been saved as \"" + fileName + "\".");
		}
		catch (IOException e) {
			throw new CommandException("The key pair could not be dumped into a file!", e);
		}
		catch (InvalidKeyException e) {
			throw new CommandException("The new key pair is invalid!", e);
		}
		catch (NoSuchAlgorithmException e) {
			throw new CommandException("The ed25519 signature algorithm is not available!", e);
		}
	}
}