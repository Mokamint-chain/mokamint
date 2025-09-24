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

package io.mokamint.plotter.api;

/**
 * A calculator of the size of a plot file.
 */
public interface PlotSize {

	/**
	 * Yields the size of the metadata information in the plot file, including the prolog
	 * of the plot file.
	 * 
	 * @return the size of the metadata information in the plot file
	 */
	int getMetadataSize();

	/**
	 * Yields the size of a scoop of the nonces of the plot file.
	 * 
	 * @return the size of a scoop
	 */
	int getScoopSize();

	/**
	 * Yields the size of a single nonce of the plot file.
	 * 
	 * @return the size of a single nonce of the plot file
	 */
	long getNonceSize();

	/**
	 * Yields the total size of the plot file, including the metadata and all the nonces.
	 * 
	 * @return the total size of the plot file
	 */
	long getTotalSize();
}