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

/**
 * A row in a table, that can be printed by a tool command.
 */
public interface Row {

	/**
	 * Yields the given column element of this row.
	 * 
	 * @param index the index of the column, from 0 upwards
	 * @return the element
	 */
	String getColumn(int index);

	/**
	 * Yields a string representation of this row, as it must
	 * be reported to the user. The position is relative to the head of the table:
	 * 0 is the first row (typically, the titles), 1 is the second row and so on.
	 * 
	 * @param pos the position, relative to the head of the table
	 * @param table the table this row belongs to
	 * @return the string representation
	 */
	String toString(int pos, Table table);
}