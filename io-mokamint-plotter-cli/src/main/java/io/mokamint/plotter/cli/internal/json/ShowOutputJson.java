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

package io.mokamint.plotter.cli.internal.json;

import java.security.NoSuchAlgorithmException;

import io.hotmoka.websockets.beans.api.InconsistentJsonException;
import io.hotmoka.websockets.beans.api.JsonRepresentation;
import io.mokamint.nonce.Prologs;
import io.mokamint.plotter.cli.api.ShowOutput;
import io.mokamint.plotter.cli.internal.ShowImpl;

/**
 * The JSON representation of the output of the {@code mokamint-plotter create} command.
 */
public abstract class ShowOutputJson implements JsonRepresentation<ShowOutput> {
	private final Prologs.Json prolog;
	private final long start;
	private final long length;
	private final String hashing;

	protected ShowOutputJson(ShowOutput output) {
		this.prolog = new Prologs.Json(output.getProlog());
		this.start = output.getStart();
		this.length = output.getLength();
		this.hashing = output.getHashing().getName();
	}

	@Override
	public ShowOutput unmap() throws InconsistentJsonException, NoSuchAlgorithmException {
		return new ShowImpl.Output(this);
	}

	public Prologs.Json getProlog() {
		return prolog;
	}

	public long getStart() {
		return start;
	}

	public long getLength() {
		return length;
	}

	public String getHashing() {
		return hashing;
	}
}