package com.example.ocrodds.data

import android.content.Context

interface AppContainer {
    val ocrOddsRepository: OcrOddsRepository
}

class DefaultAppContainer(
) : AppContainer {
    private val scpHost = "hcjp1.hbh-tech.com"
    private val scpPort = 22
    private val scpUser = "kroper"
    private val remoteBasePath = "/home/kroper/kra_odds"
    private val keyFileAssetsName = "id_rsa.kroper.hbh"

    override val ocrOddsRepository: OcrOddsRepository by lazy {
        DefaultOcrOddsRepository(
            host = scpHost,
            port = scpPort,
            user = scpUser,
            remoteBasePath = remoteBasePath,
            keyFileAssetsName = keyFileAssetsName
        )
    }
}