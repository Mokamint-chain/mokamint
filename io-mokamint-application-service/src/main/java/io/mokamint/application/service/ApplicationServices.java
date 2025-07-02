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

package io.mokamint.application.service;

import io.hotmoka.websockets.api.FailedDeploymentException;
import io.mokamint.application.api.Application;
import io.mokamint.application.service.api.ApplicationService;
import io.mokamint.application.service.internal.ApplicationServiceImpl;

/**
 * A provider of application services for the public API of an application.
 */
public abstract class ApplicationServices {

	private ApplicationServices() {}

	/**
	 * Opens and yields a new service for the given application, at the given network port.
	 * 
	 * @param application the application
	 * @param port the port
	 * @return the new service
	 * @throws FailedDeploymentException if the service cannot be deployed
	 */
	public static ApplicationService open(Application application, int port) throws FailedDeploymentException {
		return new ApplicationServiceImpl(application, port);
	}
}