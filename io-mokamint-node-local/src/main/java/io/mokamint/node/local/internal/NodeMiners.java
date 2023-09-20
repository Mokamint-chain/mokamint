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

import java.io.IOException;
import java.util.stream.Stream;

import io.hotmoka.annotations.ThreadSafe;
import io.hotmoka.exceptions.CheckSupplier;
import io.hotmoka.exceptions.UncheckedException;
import io.mokamint.miner.api.Miner;
import io.mokamint.node.MinerInfos;
import io.mokamint.node.api.MinerInfo;

/**
 * The set of miners of a local node.
 */
@ThreadSafe
public class NodeMiners implements AutoCloseable {

	/**
	 * The miners of the node.
	 */
	private final PunishableSet<Miner> miners;

	/**
	 * Creates a container for the miners of a local node.
	 * 
	 * @param node the node
	 */
	public NodeMiners(LocalNodeImpl node) {
		this.miners = new PunishableSet<Miner>(Stream.empty(), node.getConfig().getMinerInitialPoints(), (_miner, _force) -> true, this::onRemove);
	}

	/**
	 * Yields the miners.
	 * 
	 * @return the miners
	 */
	public Stream<Miner> get() {
		return miners.getElements();
	}

	/**
	 * Yields information about the miners.
	 * 
	 * @return information about the miners
	 */
	public Stream<MinerInfo> getInfos() {
		return miners.getActorsWithPoints().map(entry -> MinerInfos.of(entry.getKey().getUUID(), entry.getValue(), entry.getKey().toString()));
	}

	/**
	 * Adds the given miner to this container, if it was not already there.
	 * 
	 * @param miner the miner to add
	 * @return true if and only if the miner has been added
	 */
	public boolean add(Miner miner) {
		return miners.add(miner);
	}

	/**
	 * Removes the given miner from this container, if it was there.
	 * If removed, the miner will also be closed.
	 * 
	 * @param miner the miner to remove
	 * @return true if and only if the miner has been removed
	 * @throws IOException if the miner failed to close
	 */
	public boolean remove(Miner miner) throws IOException {
		return CheckSupplier.check(IOException.class, () -> miners.remove(miner));
	}

	/**
	 * Punishes a miner, by reducing its points. If the miner reaches zero points,
	 * it gets removed from this container. If the miner was not present in this
	 * container, nothing happens.
	 * 
	 * @param miner the miner to punish
	 * @param points how many points get removed
	 * @return true if and only if the miner was present in this container,
	 *         has reached zero points and has been removed
	 * @throws IOException if {@code miner} reached zero points but could not be closed
	 */
	public boolean punish(Miner miner, long points) throws IOException {
		return CheckSupplier.check(IOException.class, () -> miners.punish(miner, points));
	}

	/**
	 * Closes this container. All miners contained therein will be closed as well.
	 * 
	 * @throws IOException if some miner could not be closed
	 */
	@Override
	public void close() throws IOException {
		IOException exception = null;

		for (var miner: miners.getElements().toArray(Miner[]::new)) {
			try {
				miner.close();
			}
			catch (IOException e) {
				exception = e;
			}
		}

		if (exception != null)
			throw exception;
	}

	/**
	 * When a miner gets removed, it will get closed.
	 * 
	 * @param miner the removed miner
	 * @return true
	 */
	private boolean onRemove(Miner miner) {
		try {
			miner.close();
		}
		catch (IOException e) {
			throw new UncheckedException(e);
		}
	
		return true;
	}
}