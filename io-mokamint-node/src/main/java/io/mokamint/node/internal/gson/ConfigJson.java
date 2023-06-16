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

import java.security.NoSuchAlgorithmException;

import io.hotmoka.websockets.beans.JsonRepresentation;
import io.mokamint.node.Config;

/**
 * The JSON representation of a {@code Config}.
 */
public abstract class ConfigJson implements JsonRepresentation<Config> {
	private String hashingForDeadlines;
	private String hashingForGenerations;
	private String hashingForBlocks;
	private long targetBlockCreationTime;

	protected ConfigJson(Config config) {
		this.hashingForDeadlines = config.hashingForDeadlines.getName();
		this.hashingForGenerations = config.hashingForGenerations.getName();
		this.hashingForBlocks = config.hashingForBlocks.getName();
		this.targetBlockCreationTime = config.targetBlockCreationTime;
	}

	@Override
	public Config unmap() throws NoSuchAlgorithmException {
		return Config.Builder.defaults()
			.setHashingForDeadlines(hashingForDeadlines)
			.setHashingForGenerations(hashingForGenerations)
			.setHashingForBlocks(hashingForBlocks)
			.setTargetBlockCreationTime(targetBlockCreationTime)
			.build();
	}
}