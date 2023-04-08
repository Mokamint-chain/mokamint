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

package io.hotmoka.spacemint.nonce.api;

import java.io.IOException;
import java.nio.channels.FileChannel;

/**
 * A nonce. Each nonce contains scoops.
 * Each scoop contains a pair of hashes.
 */
public interface Nonce {

	/**
	 * The maximal length of the prolog of a nonce, in bytes.
	 */
	final int MAX_PROLOG_SIZE = 16777216; // 16 megabytes

	/**
	 * The number of scoops contained in a nonce.
	 */
	final static int SCOOPS_PER_NONCE = 4096;

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
	 * Yields the deadline of this nonce, for the given scoop number and data.
	 * 
	 * @param scoopNumber the number of the scoop to consider to compute the deadline
	 * @param data the data used to compute the deadline
	 * @return the deadline
	 */
	Deadline getDeadline(int scoopNumber, byte[] data);
}