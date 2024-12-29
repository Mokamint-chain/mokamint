/*
Copyright 2024 Fausto Spoto

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

/**
 * The a consensus configuration, according to the builder pattern.
 */
public class BasicConsensusConfig extends ConsensusConfigImpl<BasicConsensusConfig, BasicConsensusConfigBuilder> {

	/**
	 * Full constructor for the builder pattern.
	 * 
	 * @param builder the builder where information is extracted from
	 */
	public BasicConsensusConfig(BasicConsensusConfigBuilder builder) {
		super(builder);
	}

	@Override
	public BasicConsensusConfigBuilder toBuilder() {
		return new BasicConsensusConfigBuilder(this);
	}
}