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

package io.mokamint.node.api;

import io.hotmoka.annotations.Immutable;

/**
 * The semantical version of a Mokamint node (see {@code https://semver.org/}).
 */
@Immutable
public interface Version {

	/**
	 * Yields the major version component.
	 * 
	 * @return the major version component
	 */
	int getMajor();

	/**
	 * Yields the minor version component.
	 * 
	 * @return the minor version component
	 */
	int getMinor();

	/**
	 * Yields the patch version component.
	 * 
	 * @return the patch version component
	 */	
	int getPatch();

	/**
	 * Determines if a node with this version can work
	 * with a node of the {@code other} version, in the same network.
	 * Normally, this requires to have same major and minor components.
	 * 
	 * @param other the version of the other node
	 * @return true only if that condition holds
	 */
	boolean canWorkWith(Version other);

	@Override
	boolean equals(Object other);

	@Override
	int hashCode();

}