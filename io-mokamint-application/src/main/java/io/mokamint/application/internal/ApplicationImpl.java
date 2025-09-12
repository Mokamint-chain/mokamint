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

import java.util.concurrent.TimeoutException;

import io.hotmoka.annotations.ThreadSafe;
import io.hotmoka.closeables.AbstractAutoCloseableWithLockAndOnCloseHandlers;
import io.mokamint.application.Infos;
import io.mokamint.application.api.Application;
import io.mokamint.application.api.ClosedApplicationException;
import io.mokamint.application.api.Description;
import io.mokamint.application.api.Info;
import io.mokamint.application.api.Name;

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

	@Override
	public Info getInfo() throws ClosedApplicationException, TimeoutException, InterruptedException {
		// by default, we access the @Name and @Description annotations, but subclasses may redefine
		Name name = getClass().getAnnotation(Name.class);
		Description description = getClass().getAnnotation(Description.class);

		return Infos.of(name != null ? name.value() : "<unknown>", description != null ? description.value() : "<unknown>");
	}

	/**
	 * Closes any resources used by this application. This is called only the first time
	 * that {@link #close()} gets called.
	 */
	protected void closeResources() {
	}
}