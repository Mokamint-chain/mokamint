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

package io.mokamint.cli;

/**
 * A table of rows.
 */
public interface Table {

	/**
	 * Adds the given row at the end of this table.
	 * 
	 * @param row the row to add; it is assumed to have
	 *            the same number of columns as this table
	 */
	void add(Row row);

	/**
	 * Yields the number of characters that are used at most for the given column
	 * of this table.
	 * 
	 * @param column the column index, starting at 0
	 * @return the number of characters
	 */
	int getSlotsForColumn(int column);

	/**
	 * Calls {@link Row#toString(int, Table)} on each row of this table
	 * and concatenates the result with newlines as separators. If the table is empty
	 * (that is, if it only contains its heading), then the empty string is returned.
	 * 
	 * @return the resulting concatenation
	 */
	@Override
	String toString();

	/**
	 * Prints this object on the screen.
	 */
	void print();
}