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

package io.mokamint.node.local.internal;

import java.util.Arrays;
import java.util.Comparator;
import java.util.Optional;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.concurrent.TimeoutException;
import java.util.function.Consumer;
import java.util.logging.Logger;
import java.util.stream.Stream;

import io.hotmoka.annotations.GuardedBy;
import io.hotmoka.annotations.ThreadSafe;
import io.hotmoka.crypto.Hex;
import io.hotmoka.crypto.api.Hasher;
import io.mokamint.application.api.Application;
import io.mokamint.application.api.ClosedApplicationException;
import io.mokamint.node.MempoolEntries;
import io.mokamint.node.MempoolInfos;
import io.mokamint.node.MempoolPortions;
import io.mokamint.node.api.ApplicationTimeoutException;
import io.mokamint.node.api.Block;
import io.mokamint.node.api.ClosedNodeException;
import io.mokamint.node.api.MempoolEntry;
import io.mokamint.node.api.MempoolInfo;
import io.mokamint.node.api.MempoolPortion;
import io.mokamint.node.api.PortionRejectedException;
import io.mokamint.node.api.Request;
import io.mokamint.node.api.RequestRejectedException;
import io.mokamint.node.local.api.LocalNodeConfig;

/**
 * The mempool of a Mokamint node. It contains requests that are available
 * to be processed and eventually included in the new blocks mined for a blockchain.
 * Requests are kept and processed in decreasing order of priority.
 */
@ThreadSafe
public class Mempool {

	/**
	 * The node having this mempool.
	 */
	private final LocalNodeImpl node;

	/**
	 * The configuration of {@link #node}.
	 */
	private final LocalNodeConfig config;

	/**
	 * The blockchain of the node having this mempool.
	 */
	private final Blockchain blockchain;

	/**
	 * The application running in the node having this mempool.
	 */
	private final Application app;

	/**
	 * The hasher of the requests.
	 */
	private final Hasher<Request> hasher;

	/**
	 * The base block of the mempool: the requests inside {@link #mempool}
	 * have arrived after the creation of this block. If missing, the requests
	 * have arrived after the creation of the blockchain itself.
	 */
	@GuardedBy("this.mempool")
	private Optional<Block> base;

	/**
	 * The container of the requests inside this mempool. They are kept ordered by decreasing priority.
	 */
	@GuardedBy("itself")
	private final SortedSet<RequestEntry> mempool;

	private final static Logger LOGGER = Logger.getLogger(Mempool.class.getName());

	/**
	 * Creates a mempool for the given node, initially empty and without a base.
	 * 
	 * @param node the node
	 * @throws ClosedNodeException if the node is already closed
	 */
	public Mempool(LocalNodeImpl node) throws ClosedNodeException {
		this.node = node;
		this.config = node.getConfig();
		this.blockchain = node.getBlockchain();
		this.app = node.getApplication();
		this.hasher = node.getConfigInternal().getHashingForRequests().getHasher(Request::getBytes);
		this.base = Optional.empty();
		this.mempool = new TreeSet<>(Comparator.reverseOrder()); // decreasing priority
	}

	/**
	 * Creates a clone of the given mempool.
	 * 
	 * @param parent the mempool to clone
	 */
	public Mempool(Mempool parent) {
		this.node = parent.node;
		this.config = parent.config;
		this.blockchain = parent.blockchain;
		this.app = parent.app;
		this.hasher = parent.hasher;

		synchronized (parent.mempool) {
			this.base = parent.base;
			this.mempool = new TreeSet<>(parent.mempool);
		}
	}

	/**
	 * Sets a new base for this mempool. Calling P the highest predecessor block of both
	 * the current base and the {@code newBase}, this method will add all requests
	 * in the blocks from the current base to P (excluded) back in this mempool and remove
	 * all requests in the blocks from P (excluded) to {@code newBase} in this mempool.
	 * 
	 * @param newBase the new base that must be set for this mempool
	 * @throws InterruptedException if the current thread gets interrupted
	 * @throws ApplicationTimeoutException if the application of the Mokamint node is unresponsive
	 * @throws MisbehavingApplicationException if the application is misbehaving
	 * @throws ClosedApplicationException if the application is already closed
	 */
	public void rebaseAt(Block newBase) throws InterruptedException, ApplicationTimeoutException, ClosedApplicationException, MisbehavingApplicationException {
		synchronized (mempool) {
			blockchain.rebase(this, newBase);
		}
	}

	/**
	 * Adds the given request to this mempool, after checking its validity.
	 * 
	 * @param request the request to add
	 * @return the request entry added to the mempool
	 * @throws RequestRejectedException if the request has been rejected; this happens,
	 *                                  for instance, if the application considers the
	 *                                  request as invalid or if its priority cannot be computed
	 *                                  or if the request is already contained in the blockchain or mempool
	 * @throws InterruptedException if the current thread gets interrupted
	 * @throws ApplicationTimeoutException if the application connected to the Mokamint node is unresponsive
	 * @throws ClosedApplicationException if the application is already closed
	 * @throws ClosedDatabaseException if the database is already closed
	 */
	public RequestEntry add(Request request) throws RequestRejectedException, InterruptedException, ApplicationTimeoutException, ClosedApplicationException, ClosedDatabaseException {
		int size = request.getNumberOfBytes();
		if (size > config.getMaxRequestSize())
			throw new RequestRejectedException("The request is " + size + " bytes long, against a maximum of " + config.getMaxRequestSize());

		try {
			app.checkRequest(request);
		}
		catch (TimeoutException e) {
			throw new ApplicationTimeoutException(e);
		}

		var entry = mkRequestEntry(request);
		int maxSize = config.getMempoolSize();
		int maxBlockSize = config.getMaxBlockSize();
		var allowsRepeatedRequests = config.allowsRepeatedRequests();
		int reqSize;

		synchronized (mempool) {
			if (!allowsRepeatedRequests && base.isPresent() && blockchain.getRequestAddress(base.get(), entry.hash).isPresent())
				// the request was already in blockchain
				throw new RequestRejectedException("Repeated request " + entry);
			else if (!allowsRepeatedRequests && mempool.contains(entry))
				// the request was already in the mempool
				throw new RequestRejectedException("Repeated request " + entry);
			else if ((reqSize = request.size()) > maxBlockSize)
				throw new RequestRejectedException("Cannot add request " + entry + ": it is too large (" + reqSize + " bytes against a maximum block size of " + maxBlockSize + ")");
			else if (mempool.size() >= maxSize)
				throw new RequestRejectedException("Cannot add request " + entry + ": all " + maxSize + " slots of the mempool are full");
			else
				mempool.add(entry);
		}

		LOGGER.info("mempool: added request " + entry);
		node.onAdded(request);

		return entry;
	}

	public RequestEntry mkRequestEntry(Request request) throws RequestRejectedException, ClosedApplicationException, ApplicationTimeoutException, InterruptedException {
		long priority;

		try {
			priority = app.getPriority(request);
		}
		catch (TimeoutException e) {
			throw new ApplicationTimeoutException(e);
		}

		return new RequestEntry(request,  priority, hasher.hash(request));
	}

	public void remove(RequestEntry entry) {
		synchronized (mempool) {
			mempool.remove(entry);
		}
	}

	/**
	 * Performs an action for each request in this mempool, in decreasing priority order.
	 * 
	 * @param the action
	 */
	public void forEachRequest(Consumer<RequestEntry> action) {
		synchronized (mempool) {
			mempool.stream().forEachOrdered(action);
		}
	}

	/**
	 * Yields information about this mempool.
	 * 
	 * @return the information
	 */
	public MempoolInfo getInfo() {
		long size;
		
		synchronized (mempool) {
			size = mempool.size();
		}
	
		return MempoolInfos.of(size);
	}

	/**
	 * Yields a portion of this mempool.
	 * 
	 * @param start the initial entry slot to return
	 * @param count the maximal number of slots to return
	 * @return the portion from {@code start} (included) to {@code start + length} (excluded)
	 * @throws PortionRejectedException if the request has been rejected, for instance because {@code count} is too large
	 */
	public MempoolPortion getPortion(int start, int count) throws PortionRejectedException {
		if (start < 0 || count <= 0)
			return MempoolPortions.of(Stream.empty());

		int max = config.getMaxMempoolPortionLength();
		if (count > max)
			throw new PortionRejectedException("count cannot be larger than " + max);

		synchronized (mempool) {
			return MempoolPortions.of(mempool.stream().skip(start).limit(count).map(RequestEntry::toMempoolEntry));
		}
	}

	Optional<Block> getBase() {
		synchronized (mempool) {
			return base;
		}
	}

	void update(Block newBase, Stream<RequestEntry> toAdd, Stream<RequestEntry> toRemove) {
		synchronized (mempool) {
			toAdd.forEach(mempool::add);
			toRemove.forEach(mempool::remove);
			this.base = Optional.of(newBase);
		}
	}

	/**
	 * An entry in the mempool. It contains the request itself, its priority and its hash.
	 */
	public final static class RequestEntry implements Comparable<RequestEntry> {
		private final Request request;
		private final long priority;
		private final byte[] hash;
	
		private RequestEntry(Request request, long priority, byte[] hash) {
			this.request = request;
			this.priority = priority;
			this.hash = hash;
		}

		/**
		 * Yields the request inside this entry.
		 * 
		 * @return the request
		 */
		public Request getRequest() {
			return request;
		}
	
		/**
		 * Yields the hash of the request inside this entry.
		 * 
		 * @return the hash
		 */
		public byte[] getHash() {
			return hash.clone();
		}
	
		@Override
		public int compareTo(RequestEntry other) {
			int diff = Long.compare(priority, other.priority);
			return diff != 0 ? diff : Arrays.compare(hash, other.hash);
		}
	
		@Override
		public boolean equals(Object other) {
			return other instanceof RequestEntry te && Arrays.equals(hash, te.hash);
		}
	
		@Override
		public int hashCode() {
			return Arrays.hashCode(hash);
		}
	
		@Override
		public String toString() {
			return Hex.toHexString(hash);
		}

		/**
		 * Yields a {@link MempoolEntry} derived from this, by projecting on hash and priority.
		 * 
		 * @return the {@link MempoolEntry}
		 */
		public MempoolEntry toMempoolEntry() {
			return MempoolEntries.of(hash, priority);
		}
	}
}