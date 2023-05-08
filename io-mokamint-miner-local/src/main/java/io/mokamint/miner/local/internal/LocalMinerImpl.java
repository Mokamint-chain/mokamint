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
import java.io.UncheckedIOException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.function.BiConsumer;
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
	private final static Logger LOGGER = Logger.getLogger(LocalMinerImpl.class.getName());

	/**
	 * The plot files used by the miner.
	 */
	private final Plot[] plots;

	/**
	 * Executors that take care of executing the requests for computing deadlines.
	 */
	private final ExecutorService executors = Executors.newFixedThreadPool(4);

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
	public void requestDeadline(DeadlineDescription description, BiConsumer<Deadline, Miner> onDeadlineComputed) throws RejectedExecutionException {
		LOGGER.info("received deadline request: " + description);

		executors.submit(() -> {
			LOGGER.info("processing deadline request: " + description);

			try {
				Stream.of(plots)
					.filter(plot -> plot.getHashing().getName().equals(description.getHashing()))
					.map(plot -> getSmallestDeadline(plot, description))
					.min(Deadline::compareByValue)
					.ifPresent(deadline -> onDeadlineComputed.accept(deadline, this));
			}
			catch (UncheckedIOException e) {
				LOGGER.log(Level.SEVERE, "couldn't compute a deadline", e.getCause());
			}
		});
	}

	@Override
	public void close() {
		executors.shutdown();
		try {
			executors.awaitTermination(20, TimeUnit.SECONDS);
		}
		catch (InterruptedException e) {
			throw new RuntimeException(e);
		}
	}

	private static Deadline getSmallestDeadline(Plot plot, DeadlineDescription description) {
		try {
			return plot.getSmallestDeadline(description);
		}
		catch (IOException e) {
			throw new UncheckedIOException(e);
		}
	}
}