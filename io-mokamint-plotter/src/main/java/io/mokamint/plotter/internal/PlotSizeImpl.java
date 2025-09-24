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

package io.mokamint.plotter.internal;

import java.nio.charset.Charset;

import io.hotmoka.crypto.api.HashingAlgorithm;
import io.mokamint.nonce.api.Challenge;
import io.mokamint.nonce.api.Prolog;
import io.mokamint.plotter.api.PlotSize;

/**
 * Implementation of a calculator of the size of a plot file.
 */
public class PlotSizeImpl implements PlotSize {

	/**
	 * The size of the metadata of the plot file.
	 */
	private final int metadataSize;

	/**
	 * The size of a scoop of the nonces of the plot file.
	 */
	private final int scoopSize;

	/**
	 * The size of a single nonce of the plot file.
	 */
	private final long nonceSize;

	/**
	 * The total size of the plot file.
	 */
	private final long totalSize;

	/**
	 * Calculates the size of a plot file with the given parameters.
	 * 
	 * @param prolog generic data that identifies, for instance, the creator of the plot
	 * @param length the number of nonces of the plot file
	 * @param hashing the hashing algorithm to use for creating the nonces of the plot file
	 */
	public PlotSizeImpl(Prolog prolog, long length, HashingAlgorithm hashing) {
		this.metadataSize = 4 + prolog.toByteArray().length // prolog
				+ 8 // start
				+ 8 // length
				+ 4 + hashing.getName().getBytes(Charset.forName("UTF-8")).length; // hashing algorithm

		this.scoopSize = 2 * hashing.length();
		this.nonceSize = Challenge.SCOOPS_PER_NONCE * (long) scoopSize; // avoid losing precision
		this.totalSize = metadataSize + length * nonceSize;
	}

	@Override
	public int getMetadataSize() {
		return metadataSize;
	}

	@Override
	public int getScoopSize() {
		return scoopSize;
	}

	@Override
	public long getNonceSize() {
		return nonceSize;
	}

	@Override
	public long getTotalSize() {
		return totalSize;
	}
}