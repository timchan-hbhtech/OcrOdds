package com.example.ocrodds.model

import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient

@Serializable
data class Odds(
    val raceday: String,
    val sitecode: String,
    val race: String,
    @Transient
    val data: List<List<String>> = emptyList(),
    @Transient
    val qData: List<List<String>> = emptyList(),
    val odds: HashMap<String, String> = hashMapOf(),
    val qOdds: HashMap<String, String> = hashMapOf()
) {
    init {
        odds.clear()
        data.forEach { row ->
            if (row.size >= 2) {
                odds[row[0]] = row[1]
            }
        }
        qOdds.clear()
        qData.forEach { row ->
            if (row.size >= 2) {
                qOdds[row[0]] = row[1]
            }
        }
    }
}