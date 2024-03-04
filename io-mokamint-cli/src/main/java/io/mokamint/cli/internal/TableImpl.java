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

package io.mokamint.cli.internal;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import com.google.gson.Gson;

import io.mokamint.cli.Row;
import io.mokamint.cli.Table;

/**
 * Implementation of a table of rows.
 */
public abstract class TableImpl implements Table {

	/**
	 * The number of columns in the rows of this table.
	 */
	private final int columns;

	/**
	 * The number of characters used at most for each column of this table.
	 */
	private final int[] slotsForColumn;

	/**
	 * True if and only if the print-out of the table must occur in JSON.
	 */
	private final boolean json;

	/**
	 * The rows in the table.
	 */
	private final List<Row> rows = new ArrayList<>();

	/**
	 * Creates an empty table, without rows, with just its header.
	 * 
	 * @param header the first row of this table, that gets added to it
	 * @param columns the number of elements in the rows of this table
	 * @param json true if and only if the print-out of the table must occur in JSON
	 */
	public TableImpl(Row header, int columns, boolean json) {
		this.columns = columns;
		this.slotsForColumn = new int[columns];
		this.json = json;
		add(header);
	}

	@Override
	public void add(Row row) {
		rows.add(row);

		for (int column = 0; column < columns; column++)
			slotsForColumn[column] = Math.max(slotsForColumn[column], row.getColumn(column).length());
	}

	@Override
	public int getSlotsForColumn(int column) {
		return slotsForColumn[column];
	}

	@Override
	public String toString() {
		if (json)
			// in JSON, we do not report the header
			return new Gson().toJsonTree(rows.stream().skip(1).collect(Collectors.toList())).toString();
		else if (rows.size() > 1)
			return IntStream.iterate(0, i -> i + 1)
					.limit(rows.size())
					.mapToObj(pos -> rows.get(pos).toString(pos, this))
					.collect(Collectors.joining("\n"));
		else
			return "";
	}

	@Override
	public void print() {
		String toString = toString();
		if (!toString.isEmpty())
			System.out.println(toString);
	}
}