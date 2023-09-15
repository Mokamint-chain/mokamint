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

package io.mokamint.node.local.tests;

import java.io.IOException;
import java.util.Date;
import java.util.Optional;
import java.util.logging.FileHandler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;
import java.util.logging.SimpleFormatter;
import java.util.stream.Stream;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.TestInfo;
import org.junit.jupiter.api.extension.BeforeAllCallback;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.extension.ExtensionContext;

import io.mokamint.node.local.tests.Tests.InitTestLogging;

/**
 * Shared code of test classes.
 */
@ExtendWith(InitTestLogging.class)
public abstract class Tests {

	public static class InitTestLogging implements BeforeAllCallback {

		@Override
		public void beforeAll(ExtensionContext context) throws Exception {
			initTestLogging(context.getTestClass());
		}
	};

	private final static Logger LOGGER = Logger.getLogger(Tests.class.getName());
	private static FileHandler handler;

	private static class MyFormatter extends SimpleFormatter {
		private final static String format = "[%1$tF %1$tT] [%2$s] %3$s -- [%4$s]%5$s%n";
		private final static String SEPARATOR = System.lineSeparator();

		@Override
		public String format(LogRecord lr) {
			if (lr.getMessage().startsWith("ENTRY"))
				return SEPARATOR + lr.getMessage().substring("ENTRY".length()) + SEPARATOR + SEPARATOR;
			else {
				var thrown = lr.getThrown();

				return String.format(format,
					new Date(lr.getMillis()),
					lr.getLevel().getLocalizedName(),
					lr.getMessage(),
					lr.getSourceClassName() + " " + lr.getSourceMethodName(),
					thrown == null ? "" : thrown
				);
			}
		}
	}

	private static void initTestLogging(Optional<Class<?>> clazz) {
		String propertyFile = System.getProperty("java.util.logging.config.file");
		if (propertyFile == null && clazz.isPresent()) {
			// if the property is not set, we provide a default
			try {
				handler = new FileHandler(clazz.get().getSimpleName() + ".log", 1000000, 1);
				handler.setFormatter(new MyFormatter());
				handler.setLevel(Level.INFO);

				var logger = Logger.getLogger("");
				logger.setUseParentHandlers(false);
		        Stream.of(logger.getHandlers()).forEach(logger::removeHandler);
		        logger.addHandler(handler);
			}
			catch (SecurityException | IOException e) {
				throw new RuntimeException("Cannot configure the test logging system", e);
			}
		}
	}

    @AfterAll
    public static void closeHandler() {
    	var handler = Tests.handler;
    	if (handler != null)
    		handler.close();
    }

    @BeforeEach
	public void markEntry(TestInfo testInfo) {
		String methodName = testInfo.getTestMethod().get().getName();
		String displayName = testInfo.getDisplayName();
		String separator = System.lineSeparator();

		if (displayName.startsWith(methodName + "(")) {
			// there is no real display name
			String asterisks = "*".repeat(methodName.length());
			LOGGER.info("ENTRY" + asterisks + separator + methodName);
		}
		else {
			String asterisks = "*".repeat(displayName.length() + 2);
			LOGGER.info("ENTRY" + asterisks + separator + methodName + separator + "(" + displayName + ")");
		}
	}
}