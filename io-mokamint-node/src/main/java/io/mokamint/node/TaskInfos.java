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

package io.mokamint.node;

import java.util.function.Function;

import io.mokamint.node.api.TaskInfo;
import io.mokamint.node.internal.TaskInfoImpl;
import io.mokamint.node.internal.gson.TaskInfoDecoder;
import io.mokamint.node.internal.gson.TaskInfoEncoder;
import io.mokamint.node.internal.gson.TaskInfoJson;

/**
 * Providers of task information objects.
 */
public abstract class TaskInfos {

	private TaskInfos() {}

	/**
	 * Yields a task information object.
	 * 
	 * @param description the description of the miner
	 * @return the task information object
	 */
	public static TaskInfo of(String description) {
		return new TaskInfoImpl(description);
	}

	/**
	 * Yields a task information object.
	 * 
	 * @param description the description of the miner
	 * @param onNull the generator of the exception to throw if some argument is {@code null}
	 * @param onIllegal the generator of the exception to throw if some argument has an illegal value
	 * @return the task information object
	 * @throws ON_NULL if some argument is {@code null}
	 * @throws ON_ILLEGAL if some argument has an illegal value
	 */
	public static <ON_NULL extends Exception, ON_ILLEGAL extends Exception> TaskInfo of(String description, Function<String, ON_NULL> onNull, Function<String, ON_ILLEGAL> onIllegal) throws ON_NULL, ON_ILLEGAL {
		return new TaskInfoImpl(description, onNull, onIllegal);
	}

	/**
	 * Gson encoder.
	 */
	public static class Encoder extends TaskInfoEncoder {

		/**
		 * Creates a new encoder.
		 */
		public Encoder() {}
	}

	/**
	 * Gson decoder.
	 */
	public static class Decoder extends TaskInfoDecoder {

		/**
		 * Creates a new decoder.
		 */
		public Decoder() {}
	}

    /**
     * Json representation.
     */
	public static class Json extends TaskInfoJson {

    	/**
    	 * Creates the Json representation for the given task information.
    	 * 
    	 * @param info the task information
    	 */
    	public Json(TaskInfo info) {
    		super(info);
    	}
    }
}