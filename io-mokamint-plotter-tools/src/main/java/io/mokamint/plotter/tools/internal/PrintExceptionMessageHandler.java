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
	public int handleExecutionException(Exception ex, CommandLine cmd, ParseResult parseResult) throws Exception {
		if (ex instanceof CommandException) {
			Throwable tex = ex;
			if (tex.getCause() != null)
				tex = tex.getCause();

			if (tex instanceof CommandException)
				cmd.getErr().println(cmd.getColorScheme().errorText(tex.getMessage()));
			else
				cmd.getErr().println(cmd.getColorScheme().errorText(tex.getClass().getName() + ": " + tex.getMessage()));

			return cmd.getExitCodeExceptionMapper() != null
					? cmd.getExitCodeExceptionMapper().getExitCode(tex)
							: cmd.getCommandSpec().exitCodeOnExecutionException();
		}
		else
			throw ex;
    }
}