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

package io.mokamint.node.messages.internal.gson;

import java.util.Optional;
import java.util.stream.Stream;

import io.hotmoka.websockets.beans.AbstractRpcMessageJsonRepresentation;
import io.hotmoka.websockets.beans.api.InconsistentJsonException;
import io.mokamint.node.TaskInfos;
import io.mokamint.node.messages.api.GetTaskInfosResultMessage;
import io.mokamint.node.messages.internal.GetTaskInfosResultMessageImpl;

/**
 * The JSON representation of a {@link GetTaskInfosResultMessage}.
 */
public abstract class GetTaskInfosResultMessageJson extends AbstractRpcMessageJsonRepresentation<GetTaskInfosResultMessage> {
	private final TaskInfos.Json[] tasks;

	protected GetTaskInfosResultMessageJson(GetTaskInfosResultMessage message) {
		super(message);

		this.tasks = message.get().map(TaskInfos.Json::new).toArray(TaskInfos.Json[]::new);
	}

	public Optional<Stream<TaskInfos.Json>> getTasks() {
		return tasks == null ? Optional.empty() : Optional.of(Stream.of(tasks));
	}

	@Override
	public GetTaskInfosResultMessage unmap() throws InconsistentJsonException {
		return new GetTaskInfosResultMessageImpl(this);
	}

	@Override
	protected String getExpectedType() {
		return GetTaskInfosResultMessage.class.getName();
	}
}