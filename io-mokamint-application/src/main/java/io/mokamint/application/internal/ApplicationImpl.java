/*
Copyright 2024 Fausto Spoto

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

package io.mokamint.application.internal;

import io.hotmoka.annotations.ThreadSafe;
import io.hotmoka.closeables.AbstractAutoCloseableWithLockAndOnCloseHandlers;
import io.mokamint.application.api.Application;
import io.mokamint.application.api.ClosedApplicationException;

/**
 * Partial implementation of a Mokamint application.
 */
@ThreadSafe
public abstract class ApplicationImpl extends AbstractAutoCloseableWithLockAndOnCloseHandlers<ClosedApplicationException> implements Application {

	/**
	 * Creates the application.
	 */
	protected ApplicationImpl() {
		super(ClosedApplicationException::new);
	}

	@Override
	public final void close() {
		try {
			if (stopNewCalls()) {
				try {
					callCloseHandlers();
				}
				finally {
					closeResources();
				}
			}
		}
		catch (InterruptedException e) {
			Thread.currentThread().interrupt();
		}
	}

	/**
	 * Closes any resources used by this application. This is called only the first time
	 * that {@link #close()} gets called.
	 */
	protected void closeResources() {
	}
}