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

package io.mokamint.plotter.internal.gson;

import java.io.IOException;
import java.nio.file.Files;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.spec.InvalidKeySpecException;

import io.hotmoka.crypto.Base58ConversionException;
import io.hotmoka.crypto.HashingAlgorithms;
import io.hotmoka.crypto.HexConversionException;
import io.hotmoka.websockets.beans.api.JsonRepresentation;
import io.mokamint.nonce.Prologs;
import io.mokamint.plotter.Plots;
import io.mokamint.plotter.api.Plot;

/**
 * The JSON representation of a {@link Plot}.
 */
public abstract class PlotJson implements JsonRepresentation<Plot> {
	private final Prologs.Json prolog;
	private final long start;
	private final long length;
	private final String hashing;

	protected PlotJson(Plot plot) {
		this.prolog = new Prologs.Json(plot.getProlog());
		this.start = plot.getStart();
		this.length = plot.getLength();
		this.hashing = plot.getHashing().getName();
	}

	@Override
	public Plot unmap() throws InvalidKeyException, NoSuchAlgorithmException, InvalidKeySpecException, IOException, HexConversionException, Base58ConversionException {
		return Plots.create(Files.createTempFile("tmp", ".plot"), prolog.unmap(), start, length, HashingAlgorithms.of(hashing), __ -> {});
	}
}