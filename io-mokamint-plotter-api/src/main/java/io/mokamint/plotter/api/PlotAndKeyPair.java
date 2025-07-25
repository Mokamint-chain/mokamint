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

/**
 * 
 */

package io.mokamint.plotter.api;

import java.io.IOException;
import java.security.KeyPair;

/**
 * A plot file and the key pair that allows one to sign the deadlines
 * generated from that plot file. The public key in the key pair coincides
 * with the public key for signing deadlines in the prolog of the plot file.
 * Closing an object of this class means to close the plot returned by {@link #getPlot()}.
 */
public interface PlotAndKeyPair extends AutoCloseable {

	/**
	 * Yields the plot file.
	 * 
	 * @return the plot file
	 */
	Plot getPlot();

	/**
	 * Yields the key pair.
	 * 
	 * @return the key pair
	 */
	KeyPair getKeyPair();

	@Override
	void close() throws IOException;
}