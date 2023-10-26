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

package io.mokamint.node.messages;

import java.util.stream.Stream;

import io.mokamint.node.api.TaskInfo;
import io.mokamint.node.messages.api.GetTaskInfosResultMessage;
import io.mokamint.node.messages.internal.GetTaskInfosResultMessageImpl;
import io.mokamint.node.messages.internal.gson.GetTaskInfosResultMessageDecoder;
import io.mokamint.node.messages.internal.gson.GetTaskInfosResultMessageEncoder;
import io.mokamint.node.messages.internal.gson.GetTaskInfosResultMessageJson;

/**
 * A provider of {@link GetTaskInfosResultMessage}.
 */
public final class GetTaskInfosResultMessages {

	private GetTaskInfosResultMessages() {}

	/**
	 * Yields a {@link GetTaskInfosResultMessage}.
	 * 
	 * @param tasks the tasks in the result
	 * @param id the identifier of the message
	 * @return the message
	 */
	public static GetTaskInfosResultMessage of(Stream<TaskInfo> tasks, String id) {
		return new GetTaskInfosResultMessageImpl(tasks, id);
	}

	/**
	 * Gson encoder.
	 */
	public static class Encoder extends GetTaskInfosResultMessageEncoder {

		/**
		 * Creates a new encoder.
		 */
		public Encoder() {}
	}

	/**
	 * Gson decoder.
	 */
	public static class Decoder extends GetTaskInfosResultMessageDecoder {

		/**
		 * Creates a new decoder.
		 */
		public Decoder() {}
	}

	/**
     * Json representation.
     */
    public static class Json extends GetTaskInfosResultMessageJson {

    	/**
    	 * Creates the Json representation for the given message.
    	 * 
    	 * @param message the message
    	 */
    	public Json(GetTaskInfosResultMessage message) {
    		super(message);
    	}
    }
}