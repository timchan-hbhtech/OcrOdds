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
    val odds: LinkedHashMap<String, String> = LinkedHashMap(),
    val qOdds: LinkedHashMap<String, String> = LinkedHashMap()
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

@Serializable
data class OddsDTO(
    val raceday: String,
    val sitecode: String,
    val race: String,
    val odds: LinkedHashMap<String, String> = LinkedHashMap(),
    val qOdds: LinkedHashMap<String, String> = LinkedHashMap()
)

fun OddsDTO.toOdds(): Odds {
    val data = odds.map { listOf(it.key, it.value) }
    val qData = qOdds.map { listOf(it.key, it.value) }

    return Odds(raceday, sitecode, race, data, qData, odds, qOdds)
}