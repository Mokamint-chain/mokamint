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

package io.mokamint.nonce.api;

import java.io.IOException;
import java.nio.channels.FileChannel;

import io.hotmoka.annotations.Immutable;

/**
 * A nonce. Each nonce contains scoops. Each scoop contains a pair of hashes.
 */
@Immutable
public interface Nonce {

	/**
	 * Dumps this nonce into the give file, as the {@code offset}th nonce inside the file.
	 * 
	 * @param where the file channel where the nonce must be dumped
	 * @param metadataSize the size of the metadata of the plot file, that precedes the nonces
	 * @param offset the progressive number of the nonce inside the file; for instance,
	 *               if the file will contains nonces from progressive 100 to progressive 150,
	 *               then they are placed at offsets from 0 to 50 inside the file, respectively
	 * @param length the total number of nonces that will be contained in the file
	 * @throws IOException if the nonce could not be dumped into the file
	 */
	void dumpInto(FileChannel where, int metadataSize, long offset, long length) throws IOException;

	/**
	 * Yields the deadline of this nonce, with the given description.
	 * 
	 * @param description the description of requested deadline
	 * @param signature the signature that will be used for the resulting deadline
	 * @return the deadline
	 * @throws IllegalArgumentException if the hashing algorithm in {@code description}
	 *                                  does not match that used in the plot file for this nonce
	 */
	Deadline getDeadline(DeadlineDescription description, byte[] signature);
}