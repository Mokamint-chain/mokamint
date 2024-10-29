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

import io.hotmoka.crypto.Hex;
import io.hotmoka.crypto.HexConversionException;
import io.hotmoka.websockets.beans.api.InconsistentJsonException;
import io.hotmoka.websockets.beans.api.JsonRepresentation;
import io.mokamint.node.MempoolEntries;
import io.mokamint.node.api.MempoolEntry;

/**
 * The JSON representation of a {@link MempoolEntry}.
 */
public abstract class MempoolEntryJson implements JsonRepresentation<MempoolEntry> {

	/**
	 * The hash of the transaction in the entry, in hexadecimal form.
	 */
	private final String hash;

	/**
	 * The priority of the transaction in the entry.
	 */
	private final long priority;

	protected MempoolEntryJson(MempoolEntry entry) {
		this.hash = Hex.toHexString(entry.getHash());
		this.priority = entry.getPriority();
	}

	@Override
	public MempoolEntry unmap() throws InconsistentJsonException {
		try {
			return MempoolEntries.of(Hex.fromHexString(hash), priority);
		}
		catch (HexConversionException | NullPointerException e) {
			throw new InconsistentJsonException(e);
		}
	}
}