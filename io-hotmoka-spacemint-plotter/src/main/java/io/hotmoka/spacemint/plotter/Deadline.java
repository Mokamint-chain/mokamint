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

package io.hotmoka.spacemint.plotter;

/**
 * A deadline inside a plot file. It is a reference to a nonce
 * and a value computed for that nonce. Deadlines are ordered
 * by the lexicographical ordering of their values.
 */
public interface Deadline extends Comparable<Deadline> {
	
	/**
	 * The reference to the nonce of the deadline. It is between
	 * {@link Plot#getStart()} (inclusive) and {@link Plot#getStart()}+{@link Plot#getLength()}
	 */
	long getProgressive();

	/**
	 * The value of the deadline computed for the nonce {@link #getProgressive()}
	 * of the plot file.
	 */
	byte[] getValue();
}