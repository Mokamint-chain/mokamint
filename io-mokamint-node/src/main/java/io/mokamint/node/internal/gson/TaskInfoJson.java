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

import io.hotmoka.websockets.beans.api.InconsistentJsonException;
import io.hotmoka.websockets.beans.api.JsonRepresentation;
import io.mokamint.node.TaskInfos;
import io.mokamint.node.api.TaskInfo;

/**
 * The JSON representation of a {@link TaskInfo}.
 */
public abstract class TaskInfoJson implements JsonRepresentation<TaskInfo> {
	private final String description;

	protected TaskInfoJson(TaskInfo info) {
		this.description = info.getDescription();
	}

	@Override
	public TaskInfo unmap() throws InconsistentJsonException {
		try {
			return TaskInfos.of(description);
		}
		catch (NullPointerException e) {
			throw new InconsistentJsonException(e);
		}
	}
}