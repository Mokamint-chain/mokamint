/*
Copyright 2021 Fausto Spoto

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

import picocli.CommandLine;
import picocli.CommandLine.IExecutionExceptionHandler;
import picocli.CommandLine.ParseResult;

public class PrintExceptionMessageHandler implements IExecutionExceptionHandler {

	@Override
	public int handleExecutionException(Exception e, CommandLine cmd, ParseResult parseResult) throws Exception {
		if (e instanceof CommandException) {
			Throwable cause = e;
			if (cause.getCause() != null)
				cause = cause.getCause();

			if (cause instanceof CommandException)
				cmd.getErr().println(cmd.getColorScheme().errorText(cause.getMessage()));
			else
				cmd.getErr().println(cmd.getColorScheme().errorText(cause.getClass().getName() + ": " + cause.getMessage()));

			return cmd.getExitCodeExceptionMapper() != null
					? cmd.getExitCodeExceptionMapper().getExitCode(cause)
							: cmd.getCommandSpec().exitCodeOnExecutionException();
		}
		else
			throw e;
    }
}