package io.mokamint.miner.remote;

import io.mokamint.miner.api.Miner;

/**
 * A remote miner. It publishes an endpoint at a URL,
 * where mining services can connect and provide their deadlines.
 */
public interface RemoteMiner extends Miner {
}