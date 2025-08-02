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

package io.mokamint.node.internal.json;

import io.hotmoka.websockets.beans.api.InconsistentJsonException;
import io.hotmoka.websockets.beans.api.JsonRepresentation;
import io.mokamint.node.api.MempoolInfo;
import io.mokamint.node.internal.MempoolInfoImpl;

/**
 * The JSON representation of a {@link MempoolInfo}.
 */
public abstract class MempoolInfoJson implements JsonRepresentation<MempoolInfo> {

	/**
	 * The size (number of entries) of the mempool.
	 */
	private final long size;

	protected MempoolInfoJson(MempoolInfo info) {
		this.size = info.getSize();
	}

	public long getSize() {
		return size;
	}

	@Override
	public MempoolInfo unmap() throws InconsistentJsonException {
		return new MempoolInfoImpl(this);
	}
}