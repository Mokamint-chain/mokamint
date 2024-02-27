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

package io.mokamint.tools;

import io.mokamint.tools.internal.TableImpl;

/**
 * Partial implementation of a table of rows.
 */
public abstract class AbstractTable extends TableImpl {

	/**
	 * Creates an empty table, without rows, with just its header.
	 * 
	 * @param header the first row of this table, that gets added to it
	 * @param columns the number of elements in the rows of this table
	 * @param json true if and only if the print-out of the table must occur in JSON
	 */
	protected AbstractTable(Row header, int columns, boolean json) {
		super(header, columns, json);
	}
}