/*
Copyright 2025 Fausto Spoto

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

package io.mokamint.plotter.cli.api;

import io.hotmoka.annotations.Immutable;
import io.hotmoka.crypto.api.HashingAlgorithm;
import io.mokamint.nonce.api.Prolog;

/**
 * The output of the {@code mokamint-plotter show} command.
 */
@Immutable
public interface ChainInfoOutput {

	/**
	 * Yields the prolog of the plot file to show.
	 * 
	 * @return the prolog of the plot file to show
	 */
	Prolog getProlog();

	/**
	 * Yields the starting nonce of the plot file to show.
	 * 
	 * @return the starting nonce of the plot file to show
	 */
	long getStart();

	/**
	 * Yields the number of nonces of the plot file to show.
	 * 
	 * @return the number of nonces of the plot file to show
	 */
	long getLength();

	/**
	 * Yields the hashing algorithm of the plot file to show.
	 * 
	 * @return the hashing algorithm of the plot file to show
	 */
	HashingAlgorithm getHashing();
}