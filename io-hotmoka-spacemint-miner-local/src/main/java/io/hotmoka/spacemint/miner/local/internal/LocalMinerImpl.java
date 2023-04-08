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

package io.hotmoka.spacemint.miner.local.internal;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Stream;

import io.hotmoka.spacemint.miner.api.Miner;
import io.hotmoka.spacemint.nonce.api.Deadline;
import io.hotmoka.spacemint.plotter.api.Plot;

/**
 * The implementation of a local miner.
 * It uses a set of plot files to find deadlines on-demand.
 */
public class LocalMinerImpl implements Miner {
	private final static Logger LOGGER = Logger.getLogger(LocalMinerImpl.class.getName());
	private final Consumer<Deadline> onDeadlineComputed;
	private final Plot[] plots;

	public LocalMinerImpl(Consumer<Deadline> onDeadlineComputed, Plot... plots) {
		this.onDeadlineComputed = onDeadlineComputed;

		if (plots.length < 1)
			throw new IllegalArgumentException("a miner needs at least a plot file");

		this.plots = plots;
	}

	@Override
	public void requestDeadline(int scoopNumber, byte[] data) {
		try {
			Deadline deadline = Stream.of(plots)
				.map(plot -> getSmallestDeadline(plot, scoopNumber, data))
				.min(Deadline::compareByValue)
				.get(); // OK, since there is at least a plot file

			onDeadlineComputed.accept(deadline);
		}
		catch (UncheckedIOException e) {
			LOGGER.log(Level.SEVERE, "couldn't compute the deadline from the plot files", e.getCause());
		}
	}

	private static Deadline getSmallestDeadline(Plot plot, int scoopNumber, byte[] data) throws UncheckedIOException {
		try {
			return plot.getSmallestDeadline(scoopNumber, data);
		}
		catch (IOException e) {
			throw new UncheckedIOException(e);
		}
	}
}