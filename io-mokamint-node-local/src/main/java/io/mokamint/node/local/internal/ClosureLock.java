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

import java.util.function.Supplier;

import io.hotmoka.annotations.GuardedBy;
import io.mokamint.node.api.ClosedNodeException;

/**
 * An object used in autocloseable whose methods must be blocked
 * with an exception after the object is closed. Moreover, the close
 * operation must wait for all methods to complete before closing the object.
 */
public class ClosureLock {

	private final Object lock = new Object();

	/**
	 * True if and only if the object has been closed already.
	 */
	@GuardedBy("lock")
	private boolean isClosed;

	@GuardedBy("lock")
	private int currentCallsCount;

	/**
	 * Guarantees that the node is open if a call starts.
	 * 
	 * @throws ClosedNodeException if the node was closed
	 */
	public <E extends Exception> void beforeCall(Supplier<E> exception) throws E {
		synchronized (lock) {
			if (isClosed)
				throw exception.get();
			
			currentCallsCount++;
		}
	}

	/**
	 * At the end of the last call, it signals every thread waiting for this event.
	 */
	public void afterCall() {
		synchronized (lock) {
			if (--currentCallsCount == 0)
				lock.notifyAll();
		}
	}

	/**
	 * Stops future new calls and waits for all unfinished calls to complete.
	 * 
	 * @return true if and only if it actually stopped new calls, false if
	 *         this situation already held, because it was already requested
	 * @throws InterruptedException if the execution gets interrupted while
	 *                              waiting for unfinished calls to complete
	 */
	public boolean stopNewCalls() throws InterruptedException {
		synchronized (lock) {
			if (isClosed)
				return false;

			isClosed = true;
	
			if (currentCallsCount > 0)
				lock.wait();
	
			return true;
		}
	}
}
