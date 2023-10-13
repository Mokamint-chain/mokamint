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
import java.nio.file.Path;
import java.security.NoSuchAlgorithmException;

/**
 * The paths of a plot file and of a key pair that allows one to sign the deadlines
 * generated from that plot file, together with the password that allows one to use the key pair.
 * This information could be provided from a command-line tool and the plot file and the key pair
 * can be loaded by calling {@link #load()}.
 */
public interface PlotArgs {

	/**
	 * Yields the path of the plot file.
	 * 
	 * @return the path of the plot file
	 */
	Path getPlot();

	/**
	 * Yields the path of the key pair.
	 * 
	 * @return the path of key pair
	 */
	Path getKeyPair();

	/**
	 * Yields the password of the key pair contained in {@link #getKeyPair()}.
	 * 
	 * @return the password
	 */
	char[] getPassword();

	/**
	 * Accesses the plot and the key pair files, by using {@link #getPassword()} to
	 * unlock the key pair, and yields the resulting plot and key pair objects.
	 * 
	 * @return the plot and key pair
	 * @throws NoSuchAlgorithmException if the plot refers to a missing hashing algorithm
	 * @throws IOException if the plot or the key pair cannot be accessed, or if
	 *                     the public key of the key pair does not coincide with the
	 *                     public key in the prolog of the plot
	 */
	PlotAndKeyPair load() throws IOException, NoSuchAlgorithmException;
}