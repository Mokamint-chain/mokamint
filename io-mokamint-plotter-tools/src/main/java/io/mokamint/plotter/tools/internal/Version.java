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

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

import io.mokamint.plotter.tools.MokamintPlot;
import picocli.CommandLine.Command;

@Command(name = "version",
	description = "Print version information.",
	showDefaultValues = true)
public class Version extends AbstractCommand {

	@Override
	protected void execute() throws IOException {
		try (InputStream is = MokamintPlot.class.getClassLoader().getResourceAsStream("maven.properties")) {
			var mavenProperties = new Properties();
			mavenProperties.load(is);
			System.out.println(mavenProperties.getProperty("mokamint.version"));
		}
	}
}