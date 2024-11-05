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

import java.time.LocalDateTime;
import java.util.UUID;

import io.mokamint.node.api.NodeInfo;
import io.mokamint.node.api.Version;
import io.mokamint.node.internal.NodeInfoImpl;
import io.mokamint.node.internal.gson.NodeInfoDecoder;
import io.mokamint.node.internal.gson.NodeInfoEncoder;
import io.mokamint.node.internal.gson.NodeInfoJson;

/**
 * Providers of non-consensus node information.
 */
public abstract class NodeInfos {

	private NodeInfos() {}

	/**
	 * Yields a node information object.
	 * 
	 * @param version the version of the node
	 * @param uuid the UUID of the node
	 * @param localDateTimeUTC the local date and time UTC of the node
	 * @return the node information object
	 */
	public static NodeInfo of(Version version, UUID uuid, LocalDateTime localDateTimeUTC) {
		return new NodeInfoImpl(version, uuid, localDateTimeUTC);
	}

	/**
	 * Gson encoder.
	 */
	public static class Encoder extends NodeInfoEncoder {

		/**
		 * Creates a new encoder.
		 */
		public Encoder() {}
	}

	/**
	 * Gson decoder.
	 */
	public static class Decoder extends NodeInfoDecoder {

		/**
		 * Creates a new decoder.
		 */
		public Decoder() {}
	}

    /**
     * Json representation.
     */
    public static class Json extends NodeInfoJson {

    	/**
    	 * Creates the Json representation for the given node info.
    	 * 
    	 * @param info the node information
    	 */
    	public Json(NodeInfo info) {
    		super(info);
    	}
    }
}