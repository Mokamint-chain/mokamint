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

package io.mokamint.node.internal.gson;

import java.util.stream.Stream;

import io.hotmoka.exceptions.CheckSupplier;
import io.hotmoka.exceptions.UncheckFunction;
import io.hotmoka.websockets.beans.api.InconsistentJsonException;
import io.hotmoka.websockets.beans.api.JsonRepresentation;
import io.mokamint.node.MempoolEntries;
import io.mokamint.node.MempoolPortions;
import io.mokamint.node.api.MempoolPortion;

/**
 * The JSON representation of a {@link MempoolPortion}.
 */
public abstract class MempoolPortionJson implements JsonRepresentation<MempoolPortion> {
	private final MempoolEntries.Json[] transactions;

	protected MempoolPortionJson(MempoolPortion mempool) {
		this.transactions = mempool.getEntries().map(MempoolEntries.Json::new).toArray(MempoolEntries.Json[]::new);
	}

	@Override
	public MempoolPortion unmap() throws InconsistentJsonException {
		return CheckSupplier.check(InconsistentJsonException.class, () ->
			MempoolPortions.of(Stream.of(transactions).map(UncheckFunction.uncheck(MempoolEntryJson::unmap)))
		);
	}
}