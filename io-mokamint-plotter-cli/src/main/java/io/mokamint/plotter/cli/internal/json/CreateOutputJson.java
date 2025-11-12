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

import io.hotmoka.websockets.beans.api.InconsistentJsonException;
import io.hotmoka.websockets.beans.api.JsonRepresentation;
import io.mokamint.plotter.cli.api.CreateOutput;
import io.mokamint.plotter.cli.internal.CreateImpl;

/**
 * The JSON representation of the output of the {@code mokamint-plotter create} command.
 */
public abstract class CreateOutputJson implements JsonRepresentation<CreateOutput> {

	protected CreateOutputJson(CreateOutput output) {}

	@Override
	public CreateOutput unmap() throws InconsistentJsonException {
		return new CreateImpl.Output(this);
	}
}