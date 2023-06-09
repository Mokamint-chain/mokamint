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

package io.mokamint.node.local.internal.tasks;

import java.time.LocalDateTime;
import java.util.logging.Level;
import java.util.logging.Logger;

import io.hotmoka.annotations.OnThread;
import io.mokamint.node.api.Block;
import io.mokamint.node.local.internal.LocalNodeImpl;

public class DelayedMineNewBlockTask extends MineNewBlockTask {
	private final long delay;

	private final static Logger LOGGER = Logger.getLogger(DelayedMineNewBlockTask.class.getName());
	
	public DelayedMineNewBlockTask(LocalNodeImpl node, Block previous, LocalDateTime startTime, long delay) {
		super(node, previous, startTime);

		this.delay = delay;
	}

	@Override
	public String toString() {
		return "delayed " + super.toString();
	}

	@Override @OnThread("tasks")
	protected void body() {
		try {
			LOGGER.info(logIntro + "I will retry mining in " + delay / 1000L + " seconds");
			Thread.sleep(delay);
		}
		catch (InterruptedException e) {
			LOGGER.log(Level.WARNING, "mining interrupted", e);
			Thread.currentThread().interrupt();
		}

		super.body();
	}
}