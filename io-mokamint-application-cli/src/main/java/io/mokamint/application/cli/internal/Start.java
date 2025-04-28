/*
Copyright 2024 Fausto Spoto

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

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.logging.Level;
import java.util.logging.Logger;

import io.hotmoka.cli.AbstractCommand;
import io.hotmoka.cli.CommandException;
import io.mokamint.application.ApplicationNotFoundException;
import io.mokamint.application.Applications;
import io.mokamint.application.api.ApplicationException;
import io.mokamint.application.service.ApplicationServices;
import picocli.CommandLine.Command;
import picocli.CommandLine.Help.Ansi;
import picocli.CommandLine.Parameters;

@Command(name = "start",
	description = "Start a network service for an application.",
	showDefaultValues = true)
public class Start extends AbstractCommand {

	@Parameters(index = "0", description = "the name of the application to start")
	private String application;

	@Parameters(index = "1", description = "the port number where the service to the application will be published", defaultValue = "8050")
	private int port;

	private final static Logger LOGGER = Logger.getLogger(Start.class.getName());

	@Override
	protected void execute() throws CommandException {
		System.out.print("Publishing a service for application " + application + " at ws://localhost:" + port + "... ");

		try (var app = Applications.load(application); var service = ApplicationServices.open(app, port)) {
			System.out.println(Ansi.AUTO.string("@|blue done.|@"));

			try (var reader = new BufferedReader(new InputStreamReader(System.in))) {
				System.out.print(Ansi.AUTO.string("@|green Press ENTER to stop the application service: |@"));
				reader.readLine();
			}
			catch (IOException e) {
				System.out.println(Ansi.AUTO.string("@|red Cannot access the standard input!|@"));
				LOGGER.log(Level.SEVERE, "cannot access the standard input", e);
			}
		}
		catch (ApplicationNotFoundException e) {
			throw new CommandException("The application " + application + " has not been found", e);
		}
		catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			throw new CommandException("The application service has been interrupted", e);
		}
		catch (ApplicationException e) {
			throw new CommandException("The application is misbehaving: is port " + port + " available?", e);
		}
	}
}