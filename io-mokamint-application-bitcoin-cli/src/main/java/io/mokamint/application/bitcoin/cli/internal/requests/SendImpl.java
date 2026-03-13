/*
Copyright 2026 Fausto Spoto

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

package io.mokamint.application.bitcoin.cli.internal.requests;

import java.math.BigInteger;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.PublicKey;
import java.security.SignatureException;
import java.util.Arrays;
import java.util.concurrent.TimeoutException;

import io.hotmoka.cli.CommandException;
import io.hotmoka.crypto.SignatureAlgorithms;
import io.hotmoka.crypto.api.Entropy;
import io.mokamint.application.bitcoin.SendRequest;
import io.mokamint.node.MempoolEntries;
import io.mokamint.node.Requests;
import io.mokamint.node.api.ApplicationTimeoutException;
import io.mokamint.node.api.ClosedNodeException;
import io.mokamint.node.api.MempoolEntry;
import io.mokamint.node.api.RequestRejectedException;
import io.mokamint.node.cli.AbstractPublicRpcCommand;
import io.mokamint.node.cli.converters.ED25519PublicKeyOptionConverter;
import io.mokamint.node.cli.converters.EntropyOptionConverter;
import io.mokamint.node.remote.api.RemotePublicNode;
import jakarta.websocket.EncodeException;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

public class SendImpl extends AbstractPublicRpcCommand {

	@Parameters(index = "0", description = "the path of the ed25519 key pair of the sender", paramLabel = "<path>", converter = EntropyOptionConverter.class)
	private Entropy keyPairOfSender;

	@Parameters(index = "1", description = "the amount of coins to send")
	private BigInteger amount;

	@Parameters(index = "2", description = "the ed25519 public key of the receiver", paramLabel = "<base58 string>", converter = ED25519PublicKeyOptionConverter.class)
	private PublicKey publicKeyOfReceiver;

	@Option(names = "--nonce", description = "the progressive nonce to distinguish repeated requests", defaultValue = "0")
	private long nonce;

	@Option(names = { "--password", "--password-of-sender" }, description = "the password of the key pair of the sender", interactive = true, defaultValue = "")
	private char[] passwordOfSender;

	private void body(RemotePublicNode remote) throws CommandException, TimeoutException, InterruptedException, ClosedNodeException {
		String passwordOfSenderAsString;
		try {
			passwordOfSenderAsString = new String(passwordOfSender);
			var keyPair = keyPairOfSender.keys(passwordOfSenderAsString, SignatureAlgorithms.ed25519());
			var sr = SendRequest.of(keyPair, amount, publicKeyOfReceiver, nonce);
			MempoolEntry entry = addRequest(remote, sr);

			if (json()) {
				try {
					System.out.println(new MempoolEntries.Encoder().encode(entry));
				}
				catch (EncodeException e) {
					throw new CommandException("Cannot encode a mempool entry at \"" + publicUri() + "\" in JSON format!", e);
				}
			}
			else
				System.out.println(entry);
		}
		catch (NoSuchAlgorithmException e) {
			throw new CommandException("The signature algorithmn ed25519 is not available!", e);
		}
		catch (InvalidKeyException e) {
			throw new CommandException("The key pair file does not contain a valid ed25519 key pair!", e);
		}
		catch (SignatureException e) {
			throw new CommandException("The request could not be signed correctly!", e);
		}
		finally {
			passwordOfSenderAsString = null;
			Arrays.fill(passwordOfSender, ' ');
		}
	}

	private MempoolEntry addRequest(RemotePublicNode remote, SendRequest sendRequest) throws TimeoutException, InterruptedException, ClosedNodeException, CommandException {
		try {
			return remote.add(Requests.of(sendRequest.toByteArray()));
		}
		catch (ApplicationTimeoutException e) {
			throw new CommandException("The application of the node timed out", e);
		}
		catch (RequestRejectedException e) {
			throw new CommandException("The request has been rejected: " + e.getMessage());
		}
	}

	@Override
	protected void execute() throws CommandException {
		execute(this::body);
	}
}