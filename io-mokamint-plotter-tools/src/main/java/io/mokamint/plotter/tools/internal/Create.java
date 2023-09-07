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

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.NoSuchAlgorithmException;
import java.security.spec.InvalidKeySpecException;
import java.util.function.Function;

import io.hotmoka.crypto.Base58;
import io.hotmoka.crypto.HashingAlgorithms;
import io.hotmoka.crypto.SignatureAlgorithms;
import io.mokamint.plotter.Plots;
import io.mokamint.tools.AbstractCommand;
import io.mokamint.tools.CommandException;
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

	@Parameters(index = "3", description = "the chain identifier of the network for which the plot will be used")
	private String chainId;

	@Parameters(index = "4", description = "the base58-encoded public key of the node for which the plot will be used")
	private String nodePublicKeyBase58;

	@Parameters(index = "5", description = "the base58-encoded public key of the plot")
	private String plotPublicKeyBase58;

	@Option(names = "--extra", description = "application-specific base58-encoded extra data for the plot", defaultValue = "")
	private String extraBase58;

	@Option(names = "--hashing", description = "the name of the hashing algorithm for the nonces", defaultValue = "shabal256")
	private String hashing;

	@Override
	protected void execute() {
		try {
			Files.deleteIfExists(path);
		}
		catch (IOException e) {
			throw new CommandException("Failed to overwrite \"" + path + "\"!", e);
		}

		byte[] prolog;
		
		try {
			prolog = computeProlog();
		}
		catch (IOException e) {
			throw new CommandException("Cannot compute the prolog of the plot!", e);
		}

		try (var plot = Plots.create(path, prolog, start, length, HashingAlgorithms.of(hashing, Function.identity()), this::onNewPercent)) {
		}
		catch (NoSuchAlgorithmException e) {
			throw new CommandException("The required hashing algorithm \"" + hashing + "\" does not exist!", e);
		}
		catch (IOException e) {
			throw new CommandException("Cannot write the plot file!", e);
		}

		System.out.println();
	}

	private void onNewPercent(int percent) {
		if (percent % 5 == 0)
			System.out.print(Ansi.AUTO.string("@|bold,red " + percent + "%|@ "));
		else
			System.out.print(percent + "% ");
	}

	private byte[] computeProlog() throws IOException {
		try (var os = new ByteArrayOutputStream()) {
			var chainIdBytes = chainId.getBytes(Charset.forName("UTF-8"));
			os.write(chainIdBytes.length);
			os.write(chainIdBytes);
			os.write(getKeyAsBytes(nodePublicKeyBase58));
			os.write(getKeyAsBytes(plotPublicKeyBase58));
			var extraBytes = bytesFromBase58(extraBase58);
			os.write(extraBytes.length);
			os.write(extraBytes);
	
			return os.toByteArray();
		}
	}

	private byte[] bytesFromBase58(String base58) {
		try {
			return Base58.decode(base58);
		}
		catch (IllegalArgumentException e) {
			throw new CommandException("The string " + base58 + " is not in Base58 format!", e);
		}
	}

	private byte[] getKeyAsBytes(String base58EncodedPublicKey) {
		byte[] publicKeyBytes = bytesFromBase58(base58EncodedPublicKey);
	
		if (publicKeyBytes.length != 32)
			throw new CommandException("The public key " + base58EncodedPublicKey + " is not 32 bytes long!");
	
		try {
			SignatureAlgorithms.ed25519(Function.identity()).publicKeyFromEncoding(publicKeyBytes);
		}
		catch (NoSuchAlgorithmException e) {
			throw new CommandException("The ed25519 signature algorithm is not available!", e);
		}
		catch (InvalidKeySpecException e) {
			throw new CommandException("The public key " + base58EncodedPublicKey + " is not a legal ed25519 public key!", e);
		}
	
		return publicKeyBytes;
	}
}