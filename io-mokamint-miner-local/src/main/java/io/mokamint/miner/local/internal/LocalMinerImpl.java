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
import java.math.BigInteger;
import java.security.InvalidKeyException;
import java.security.PublicKey;
import java.security.SignatureException;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import java.util.logging.Logger;
import java.util.stream.Stream;

import io.hotmoka.crypto.api.SignatureAlgorithm;
import io.hotmoka.exceptions.CheckRunnable;
import io.hotmoka.exceptions.UncheckFunction;
import io.mokamint.miner.MiningSpecifications;
import io.mokamint.miner.api.BalanceProvider;
import io.mokamint.miner.api.ClosedMinerException;
import io.mokamint.miner.api.GetBalanceException;
import io.mokamint.miner.api.MiningSpecification;
import io.mokamint.miner.local.api.LocalMiner;
import io.mokamint.nonce.api.Challenge;
import io.mokamint.nonce.api.Deadline;
import io.mokamint.plotter.api.IncompatibleChallengeException;
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
	 * The mining specification for the deadlines that must be computed by the miner.
	 */
	private final MiningSpecification miningSpecification;

	/**
	 * The provider the balance of the public keys.
	 */
	private final BalanceProvider balanceProvider;

	/**
	 * The plot files used by the miner, with their associated key pair for signing
	 * the deadlines derived from them.
	 */
	private final PlotAndKeyPair[] plotsAndKeyPairs;

	/**
	 * The prefix reported in the log messages.
	 */
	private final String logPrefix = "local miner " + uuid + ": ";

	/**
	 * True if and only if this miner has been closed already.
	 */
	private final AtomicBoolean isClosed = new AtomicBoolean();

	private final static Logger LOGGER = Logger.getLogger(LocalMinerImpl.class.getName());

	/**
	 * Builds a local miner.
	 * 
	 * @param balanceProvider the provider of the balance of the public keys
	 * @param plotsAndKeyPairs the plot files used for mining and their associated key for signing the
	 *                         deadlines generated from them; this cannot be empty
	 */
	public LocalMinerImpl(BalanceProvider balanceProvider, PlotAndKeyPair... plotsAndKeyPairs) {
		this.balanceProvider = balanceProvider;
		this.plotsAndKeyPairs = plotsAndKeyPairs;
		this.miningSpecification = extractMiningSpecification();

		LOGGER.info("created miner " + uuid);
	}

	/**
	 * Scans the provided plots and extracts the mining specification, that must be
	 * shared among all of them.
	 * 
	 * @return the mining specification
	 */
	private MiningSpecification extractMiningSpecification() {
		MiningSpecification[] specifications = Stream.of(plotsAndKeyPairs)
			.map(PlotAndKeyPair::getPlot)
			.map(this::extractMiningSpecification)
			.distinct()
			.toArray(MiningSpecification[]::new);

		if (specifications.length == 0)
			throw new IllegalArgumentException("A local miner needs at least one plot file");
		else if (specifications.length > 1)
			throw new IllegalArgumentException("The provided plots do not have the same shared mining specification");
		else
			return specifications[0];
	}

	private MiningSpecification extractMiningSpecification(Plot plot) {
		var prolog = plot.getProlog();
		return MiningSpecifications.of("", prolog.getChainId(), plot.getHashing(), prolog.getSignatureForBlocks(), prolog.getSignatureForDeadlines(), prolog.getPublicKeyForSigningBlocks());
	}

	@Override
	public UUID getUUID() {
		return uuid;
	}

	@Override
	public void requestDeadline(Challenge challenge, Consumer<Deadline> onDeadlineComputed) throws ClosedMinerException {
		if (isClosed.get())
			throw new ClosedMinerException();

		LOGGER.info(logPrefix + "received challenge: " + challenge);
		var hashingForDeadlines = challenge.getHashingForDeadlines();
		var plots = Stream.of(plotsAndKeyPairs)
			.filter(plotAndKeyPair -> plotAndKeyPair.getPlot().getHashing().equals(hashingForDeadlines))
			.toArray(PlotAndKeyPair[]::new);

		if (plots.length == 0)
			LOGGER.warning(logPrefix + "no matching plot for hashing " + hashingForDeadlines);
		else {
			try {
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
		catch (IOException | InvalidKeyException | SignatureException | IncompatibleChallengeException e) {
			LOGGER.warning(logPrefix + "cannot use a plot file: " + e.getMessage());
			return Optional.empty();
		}
	}

	@Override
	public void close() {
		isClosed.set(true);
	}

	@Override
	public String toString() {
		long nonces = Stream.of(plotsAndKeyPairs).map(PlotAndKeyPair::getPlot).mapToLong(Plot::getLength).sum();
		String howManyPlots = plotsAndKeyPairs.length == 1 ? "1 plot" : (plotsAndKeyPairs.length + " plots");
		String howManyNonces = nonces == 1 ? "1 nonce" : (nonces + " nonces");
		
		return "a local miner with " + howManyPlots + " and up to " + howManyNonces;
	}

	@Override
	public MiningSpecification getMiningSpecification() throws ClosedMinerException {
		if (isClosed.get())
			throw new ClosedMinerException();

		return miningSpecification;
	}

	@Override
	public Optional<BigInteger> getBalance(SignatureAlgorithm signature, PublicKey publicKey) throws GetBalanceException, ClosedMinerException, InterruptedException {
		if (isClosed.get())
			throw new ClosedMinerException();

		return balanceProvider.get(signature, publicKey);
	}
}