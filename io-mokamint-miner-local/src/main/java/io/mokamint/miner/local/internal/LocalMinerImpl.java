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

package io.mokamint.miner.local.internal;

import java.io.IOException;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Stream;

import io.mokamint.miner.api.Miner;
import io.mokamint.nonce.api.Deadline;
import io.mokamint.nonce.api.DeadlineDescription;
import io.mokamint.plotter.api.Plot;

/**
 * The implementation of a local miner.
 * It uses a set of plot files to find deadlines on-demand.
 */
public class LocalMinerImpl implements Miner {

	/**
	 * The unique identifier of the miner.
	 */
	private final UUID uuid = UUID.randomUUID();

	/**
	 * The plot files used by the miner.
	 */
	private final Plot[] plots;

	private final static Logger LOGGER = Logger.getLogger(LocalMinerImpl.class.getName());

	/**
	 * Builds a local miner.
	 * 
	 * @param plots the plot files used for mining. This cannot be empty
	 */
	public LocalMinerImpl(Plot... plots) {
		if (plots.length < 1)
			throw new IllegalArgumentException("a miner needs at least a plot file");

		this.plots = plots;
	}

	@Override
	public UUID getUUID() {
		return uuid;
	}

	@Override
	public void requestDeadline(DeadlineDescription description, Consumer<Deadline> onDeadlineComputed) {
		LOGGER.info("received deadline request: " + description);

		Stream.of(plots)
			.filter(plot -> plot.getHashing().getName().equals(description.getHashing().getName()))
			.map(plot -> getSmallestDeadline(plot, description))
			.flatMap(Optional::stream)
			.min(Deadline::compareByValue)
			.ifPresent(onDeadlineComputed::accept);
	}

	/**
	 * Yields the smallest deadline from the given plot file. If the plot file
	 * cannot be read, an empty optional is returned.
	 * 
	 * @param plot the plot file
	 * @param description the description of the deadline
	 * @return the deadline, if any
	 */
	private Optional<Deadline> getSmallestDeadline(Plot plot, DeadlineDescription description) {
		try {
			return Optional.of(plot.getSmallestDeadline(description));
		}
		catch (IOException e) {
			LOGGER.log(Level.SEVERE, "cannot access a plot file", e);
			return Optional.empty();
		}
	}

	@Override
	public void close() {
	}

	@Override
	public String toString() {
		long nonces = Stream.of(plots).mapToLong(Plot::getLength).sum();
		String howManyPlots = plots.length == 1 ? "1 plot" : (plots.length + " plots");
		String howManyNonces = nonces == 1 ? "1 nonce" : (nonces + " nonces");
		
		return "a local miner with " + howManyPlots + ", with up to " + howManyNonces;
	}
}