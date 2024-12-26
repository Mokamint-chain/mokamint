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

package io.mokamint.node.messages.internal;

import java.util.Arrays;
import java.util.Objects;
import java.util.stream.Stream;

import io.hotmoka.websockets.beans.AbstractRpcMessage;
import io.hotmoka.websockets.beans.api.InconsistentJsonException;
import io.mokamint.node.TaskInfos;
import io.mokamint.node.api.PublicNode;
import io.mokamint.node.api.TaskInfo;
import io.mokamint.node.messages.api.GetTaskInfosResultMessage;
import io.mokamint.node.messages.internal.gson.GetTaskInfosResultMessageJson;

/**
 * Implementation of the network message corresponding to the result of the {@link PublicNode#getTaskInfos()} method.
 */
public class GetTaskInfosResultMessageImpl extends AbstractRpcMessage implements GetTaskInfosResultMessage {

	private final TaskInfo[] tasks;

	/**
	 * Creates the message.
	 * 
	 * @param tasks the tasks in the message
	 * @param id the identifier of the message
	 */
	public GetTaskInfosResultMessageImpl(Stream<TaskInfo> tasks, String id) {
		super(id);

		this.tasks = tasks
			.map(Objects::requireNonNull)
			.toArray(TaskInfo[]::new);
	}

	/**
	 * Creates a message from the given JSON representation.
	 * 
	 * @param json the JSON representation
	 * @throws InconsistentJsonException if {@code json} is inconsistent
	 */
	public GetTaskInfosResultMessageImpl(GetTaskInfosResultMessageJson json) throws InconsistentJsonException {
		super(json.getId());

		var maybeTasks = json.getTasks();
		if (maybeTasks.isEmpty())
			throw new InconsistentJsonException("tasks must be specified");

		var tasks = maybeTasks.get().toArray(TaskInfos.Json[]::new);
		this.tasks = new TaskInfo[tasks.length];
		for (int pos = 0; pos < tasks.length; pos++) {
			var task = tasks[pos];
			if (task == null)
				throw new InconsistentJsonException("tasks cannot hold null elements");

			this.tasks[pos] = task.unmap();
		}
	}

	@Override
	public Stream<TaskInfo> get() {
		return Stream.of(tasks);
	}

	@Override
	public boolean equals(Object other) {
		return other instanceof GetTaskInfosResultMessage tirm && super.equals(other) && Arrays.equals(tasks, tirm.get().toArray(TaskInfo[]::new));
	}

	@Override
	protected String getExpectedType() {
		return GetTaskInfosResultMessage.class.getName();
	}
}