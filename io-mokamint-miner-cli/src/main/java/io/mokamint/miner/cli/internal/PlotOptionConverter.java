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

package io.mokamint.miner.cli.internal;

import java.io.IOException;
import java.nio.file.Paths;
import java.security.NoSuchAlgorithmException;

import io.mokamint.plotter.Plots;
import io.mokamint.plotter.api.Plot;
import picocli.CommandLine.ITypeConverter;

/**
 * A converter of a string path option into a plot file at that path.
 */
public class PlotOptionConverter implements ITypeConverter<Plot> {

	@Override
	public Plot convert(String path) throws IOException, NoSuchAlgorithmException {
		return Plots.load(Paths.get(path));
	}
}