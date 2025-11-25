package com.example.ocrodds

object Utils {
    fun findColumnDuplicates(table: List<List<String>>, col:Int = 0): List<Int> {
        return table.withIndex()
            .groupBy(
                keySelector = { it.value.getOrNull(col) ?: "" }, // first column, safely
                valueTransform = { it.index }
            )
            .filter { it.value.size > 1 }
            .values.flatten()
    }

    fun findDecreasingIndices(data: List<List<String>>, col:Int = 0): List<Int> {
        val result = mutableListOf<Int>()
        for (i in 1 until data.size) {
            val prev = data[i - 1].getOrNull(col)?.toDoubleOrNull()
            val curr = data[i].getOrNull(col)?.toDoubleOrNull()
            if (prev != null && curr != null && curr < prev) {
                result.add(i)
            }
        }
        return result
    }

    fun flattenColumnWise(matrix: List<List<String>>): List<String> {
        val numCols = matrix.maxOfOrNull { it.size } ?: 0
        val numRows = matrix.size

        return (0 until numCols).flatMap { col ->
            (0 until numRows).mapNotNull { row ->
                matrix.getOrNull(row)?.getOrNull(col)
            }
        }
    }

    private fun String.parseStrictInt(): Int? {
        return if (this.matches(Regex("[1-9][0-9]*|0"))) {
            this.toInt()
        } else {
            null
        }
    }

    fun isValidCombo(combo: String): Boolean {
        return combo.split("+").all {
            val num = it.parseStrictInt()
            num != null && num in 1..20
        }
    }

    fun findMissingCombinations(pairs: List<String>): List<String> {
        // Extract unique elements from the input
        val elements = pairs.flatMap { it.split("-") }.toSet().sorted()

        // Generate all possible combinations
        val allCombinations = mutableSetOf<String>()
        for (i in elements.indices) {
            for (j in i + 1 until elements.size) {
                allCombinations.add("${elements[i]}-${elements[j]}")
            }
        }

        // Normalize input pairs to the same format (sorted order)
        val existingPairs = pairs.map {
            val (a, b) = it.split("-").sorted()
            "$a-$b"
        }.toSet()

        // Find missing combinations
        return allCombinations.filterNot { it in existingPairs }
    }

    private fun cleanTable(table: List<List<String>>): List<List<String>> =
        table.map { row -> row.map { it.trim() } }

    fun mergeTables(
        table1: List<List<String>>,
        table2: List<List<String>>
    ): Pair<List<List<String>>, List<List<String>>> {
        val cleaned1 = cleanTable(table1)
        val cleaned2 = cleanTable(table2)

        val result = cleaned1.toMutableList()
        val affectedRows = mutableListOf<List<String>>()

        // Build a set of first-two-column keys from table1
        val existingKeys = cleaned1.mapNotNull { it.takeIf { it.size >= 2 }?.subList(0, 2) }.toSet()

        // Filter table2 for valid rows
        val toInsert = cleaned2.filter { row ->
            row.size >= 2 &&
                    row[0].isNotBlank() &&
                    row[1].isNotBlank() &&
                    row.subList(0, 2) !in existingKeys
        }

        for (row in toInsert) {
            val index = result.indexOfFirst { it[0] == row[0] }
            if (index != -1) {
                // Case 1: match first column → insert right after
                result.add(index + 1, row)
                affectedRows.add(row.subList(0, 2))
            } else {
                // Case 2: no match → insert by numeric order of second column
                val rowValue = row[1].toDoubleOrNull()
                if (rowValue != null) {
                    val insertIndex = result.indexOfFirst { it[1].toDoubleOrNull()?.let { v -> v > rowValue } == true }
                    if (insertIndex != -1) {
                        result.add(insertIndex, row)
                        affectedRows.add(row.subList(0, 2))
                    } else {
                        // If all values are smaller, append at the end
                        result.add(row)
                        affectedRows.add(row.subList(0, 2))
                    }
                } else {
                    // If second column is not numeric, just append
                    result.add(row)
                    affectedRows.add(row.subList(0, 2))
                }
            }
        }

        return Pair(result, affectedRows)
    }


}