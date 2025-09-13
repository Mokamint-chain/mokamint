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

package io.mokamint.application.cli.internal;

import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeoutException;

import io.hotmoka.cli.AbstractRpcCommand;
import io.hotmoka.cli.CommandException;
import io.mokamint.application.Infos;
import io.mokamint.application.api.ClosedApplicationException;
import io.mokamint.application.remote.RemoteApplications;
import io.mokamint.application.remote.api.RemoteApplication;
import jakarta.websocket.EncodeException;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

@Command(name = "info", description = "Show information about an application.")
public class Info extends AbstractRpcCommand<RemoteApplication> {

	@Option(names = "--uri", description = "the network URI where the application's API is published", defaultValue = "ws://localhost:8032")
	private URI uri;

	@Option(names = "--redirection", paramLabel = "<path>", description = "the path where the output must be redirected, if any; if missing, the output is printed to the standard output")
	private Path redirection;

	@Option(names = "--json", description = "print the output in JSON", defaultValue = "false")
	private boolean json;

	private void body(RemoteApplication remote) throws TimeoutException, InterruptedException, CommandException {
		try {
			var info = remote.getInfo();

			String result;
			if (json)
				result = new Infos.Encoder().encode(info).toString() + "\n";
			else
				result = info.toString() + "\n";

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
		catch (ClosedApplicationException e) {
			throw new CommandException("The application at " + uri + " is already closed.", e);
		}
		catch (EncodeException e) {
			throw new CommandException("Cannot encode the information of the application at \"" + uri + "\" in JSON format.", e);
		}
	}

	@Override
	protected void execute() throws CommandException {
		execute(RemoteApplications::of, this::body, uri);
	}
}