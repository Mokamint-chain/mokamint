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

import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.SignatureException;
import java.util.logging.Logger;

import io.mokamint.node.api.DatabaseException;

public class DelayedMineNewBlockTask extends MineNewBlockTask {
	private final long delay;

	private final static Logger LOGGER = Logger.getLogger(DelayedMineNewBlockTask.class.getName());
	
	public DelayedMineNewBlockTask(LocalNodeImpl node) {
		super(node);

		this.delay = node.getConfig().getDeadlineWaitTimeout();
	}

	@Override
	public void body() throws NoSuchAlgorithmException, DatabaseException, ClosedDatabaseException, InterruptedException, InvalidKeyException, SignatureException, VerificationException {
		LOGGER.info("mining: I will start mining in " + delay / 1000L + " seconds");
		Thread.sleep(delay);

		super.body();
	}
}