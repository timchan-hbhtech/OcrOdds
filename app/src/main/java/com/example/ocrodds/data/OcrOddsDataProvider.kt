package com.example.ocrodds.data

import com.example.ocrodds.model.Odds

object OcrOddsDataProvider {

    fun getSites(): List<String> {
        return listOf(
            "BUSAN", "SEOUL", "JEJU"
        )
    }

    fun getRacenums(): List<String> {
        return (1..20).map { it.toString() }
    }

    fun defaultOdds(): Odds {
        return Odds(
            raceday = "19700101",
            race = "1",
            sitecode = "BUSAN",
            data = listOf(
                listOf("1", "1.2", "1", "2.1"),
                listOf("2", "2.1", "2", "2.8"),
                listOf("3", "6.2", "3", "7.3"),
                listOf("5", "7.2", "4", "2.1"),
                listOf("6", "7.2", "5", "2.8"),
                listOf("7", "8.2", "6", "7.3"),
                listOf("7", "9.2", "10", "2.1"),
                listOf("2", "10.2", "11", "2.8"),
                listOf("8", "11.2", "12", "7.3"),
            )
        )
    }

    fun getOddsTableHeader(): List<String> {
        return listOf("TAB", "WIN", "PTAB", "PLC")
    }

    fun defaultQOdds(): Odds {
        return Odds(
            raceday = "19700101",
            race = "1",
            sitecode = "BUSAN",
            qData = listOf(
                listOf("1+10", "7.4"),
                listOf("3+10", "7.5"),
                listOf("3+10", "7.9"),
                listOf("1+3", "231.8"),
                listOf("10+11", "29.1"),
                listOf("1+5", "33.2"),
                listOf("1+91", "38.1"),
                listOf("5+8", "62.9"),
                listOf("1", "75.3"),
            )
        )
    }

    fun getQOddsTableHeader(): List<String> {
        return listOf("TAB", "QIN")
    }
}