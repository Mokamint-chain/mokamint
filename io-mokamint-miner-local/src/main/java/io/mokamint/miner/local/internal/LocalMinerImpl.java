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
import java.security.InvalidKeyException;
import java.security.SignatureException;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Stream;

import io.hotmoka.exceptions.CheckRunnable;
import io.hotmoka.exceptions.UncheckFunction;
import io.mokamint.miner.local.api.LocalMiner;
import io.mokamint.nonce.api.Challenge;
import io.mokamint.nonce.api.Deadline;
import io.mokamint.plotter.api.Plot;
import io.mokamint.plotter.api.PlotAndKeyPair;

/**
 * The implementation of a local miner.
 * It uses a set of plot files to find deadlines on-demand.
 */
public class LocalMinerImpl implements LocalMiner {

	/**
	 * The unique identifier of the miner.
	 */
	private final UUID uuid = UUID.randomUUID();

	/**
	 * The plot files used by the miner, with their associated key pair for signing
	 * the deadlines derived from them.
	 */
	private final PlotAndKeyPair[] plotsAndKeyPairs;

	/**
	 * The prefix reported in the log messages.
	 */
	private final String logPrefix = "local miner " + uuid + ": ";

	private final static Logger LOGGER = Logger.getLogger(LocalMinerImpl.class.getName());

	/**
	 * Builds a local miner.
	 * 
	 * @param plotsAndKeyPairs the plot files used for mining and their associated key for signing the
	 *                         deadlines generated from them; this cannot be empty
	 */
	public LocalMinerImpl(PlotAndKeyPair... plotsAndKeyPairs) {
		if (plotsAndKeyPairs.length < 1)
			throw new IllegalArgumentException("A local miner needs at least one plot file");

		this.plotsAndKeyPairs = plotsAndKeyPairs;
		LOGGER.info("created miner " + uuid);
	}

	@Override
	public UUID getUUID() {
		return uuid;
	}

	@Override
	public void requestDeadline(Challenge challenge, Consumer<Deadline> onDeadlineComputed) {
		LOGGER.info(logPrefix + "received challenge: " + challenge);
		var hashingForDeadlines = challenge.getHashingForDeadlines();
		var plots = Stream.of(plotsAndKeyPairs)
			.filter(plotAndKeyPair -> plotAndKeyPair.getPlot().getHashing().equals(hashingForDeadlines))
			.toArray(PlotAndKeyPair[]::new);

		if (plots.length == 0)
			LOGGER.warning(logPrefix + "no matching plot for hashing " + hashingForDeadlines);
		else try {
			CheckRunnable.check(InterruptedException.class, () ->
				Stream.of(plots)
					.map(UncheckFunction.uncheck(InterruptedException.class, plotAndKeyPair -> getSmallestDeadline(plotAndKeyPair, challenge)))
					.flatMap(Optional::stream)
					.min(Deadline::compareByValue)
					.ifPresent(onDeadlineComputed::accept));
		}
		catch (InterruptedException e) {
			Thread.currentThread().interrupt();
		}
	}

	/**
	 * Yields the smallest deadline from the given plot file. If the plot file
	 * cannot be read, an empty optional is returned.
	 * 
	 * @param plotAndKeyPair the plot file with its associated key pair for signing deadlines
	 * @param challenge the challenge for which the deadline is requested
	 * @return the deadline, if any
	 * @throws InterruptedException if the thread is interrupted while computing the smallest deadline
	 */
	private Optional<Deadline> getSmallestDeadline(PlotAndKeyPair plotAndKeyPair, Challenge challenge) throws InterruptedException {
		try {
			return Optional.of(plotAndKeyPair.getPlot().getSmallestDeadline(challenge, plotAndKeyPair.getKeyPair().getPrivate()));
		}
		catch (IOException | InvalidKeyException | SignatureException e) {
			LOGGER.log(Level.SEVERE, logPrefix + "cannot access a plot file: ", e.getMessage());
			return Optional.empty();
		}
	}

	@Override
	public void close() {
	}

	@Override
	public String toString() {
		long nonces = Stream.of(plotsAndKeyPairs).map(PlotAndKeyPair::getPlot).mapToLong(Plot::getLength).sum();
		String howManyPlots = plotsAndKeyPairs.length == 1 ? "1 plot" : (plotsAndKeyPairs.length + " plots");
		String howManyNonces = nonces == 1 ? "1 nonce" : (nonces + " nonces");
		
		return "a local miner with " + howManyPlots + " and up to " + howManyNonces;
	}
}