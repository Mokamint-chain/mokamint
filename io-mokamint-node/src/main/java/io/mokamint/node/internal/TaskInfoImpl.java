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

package io.mokamint.node.internal;

import java.util.function.Function;

import io.hotmoka.annotations.Immutable;
import io.mokamint.node.api.TaskInfo;

/**
 * An implementation of task information.
 */
@Immutable
public class TaskInfoImpl implements TaskInfo {

	/**
	 * The description of the task.
	 */
	private final String description;

	/**
	 * Creates a task information object.
	 * 
	 * @param description the description of the task
	 */
	public TaskInfoImpl(String description) {
		this(description, NullPointerException::new, IllegalArgumentException::new);
	}

	/**
	 * Creates a task information object.
	 * 
	 * @param description the description of the task
	 * @param onNull the generator of the exception to throw if some argument is {@code null}
	 * @param onIllegal the generator of the exception to throw if some argument has an illegal value
	 * @throws ON_NULL if some argument is {@code null}
	 * @throws ON_ILLEGAL if some argument has an illegal value
	 */
	public <ON_NULL extends Exception, ON_ILLEGAL extends Exception> TaskInfoImpl(String description, Function<String, ON_NULL> onNull, Function<String, ON_ILLEGAL> onIllegal) throws ON_NULL, ON_ILLEGAL {
		if (description == null)
			throw onNull.apply("decription cannot be null");

		this.description = description;
	}

	@Override
	public String getDescription() {
		return description;
	}

	@Override
	public boolean equals(Object other) {
		return other instanceof TaskInfo info && description.equals(info.getDescription());
	}

	@Override
	public int hashCode() {
		return description.hashCode();
	}

	@Override
	public int compareTo(TaskInfo other) {
		return description.compareTo(other.getDescription());
	}

	@Override
	public String toString() {
		return getDescription();
	}
}