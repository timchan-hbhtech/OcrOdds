package com.example.ocrodds.data

import android.annotation.SuppressLint
import android.content.Context
import com.example.ocrodds.model.Odds
import com.jcraft.jsch.ChannelSftp
import com.jcraft.jsch.JSch
import com.jcraft.jsch.Session
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.Properties
import android.util.Log
import com.example.ocrodds.model.OddsDTO
import com.example.ocrodds.model.toOdds

interface OcrOddsRepository {
    suspend fun uploadToSCP(
        context: Context,
        dataString: String,
        remoteFilePath: String
    ): Result<Unit>

    suspend fun moveFileOnSCP(
        context: Context,
        sourcePath: String,
        destinationPath: String
    ): Result<Unit>

    suspend fun copyFileOnSCP(
        context: Context,
        sourcePath: String,
        destinationPath: String
    ): Result<Unit>

    suspend fun downloadOddsFromSCP(
        context: Context,
        remoteFilePath: String
    ): Result<Odds>
}

class DefaultOcrOddsRepository(
    private val host: String,
    private val port: Int,
    private val user: String,
    private val remoteBasePath: String,
    private val keyFileAssetsName: String
) : OcrOddsRepository {
    @SuppressLint("SetWorldReadable", "SetWorldWritable")
    private suspend fun <T> withSftpSession(
        context: Context,
        block: (ChannelSftp) -> T
    ): Result<T> = withContext(Dispatchers.IO) {
        var tempKeyFile: File? = null
        var session: Session? = null
        var sftpChannel: ChannelSftp? = null
        try {
            // Copy id_rsa from assets to a temporary file
            tempKeyFile = File(context.filesDir, "id_rsa_temp")
            context.assets.open(keyFileAssetsName).use { input ->
                tempKeyFile.outputStream().use { output ->
                    input.copyTo(output)
                }
            }
            tempKeyFile.setReadable(true, false)
            tempKeyFile.setWritable(true, false)
            tempKeyFile.setExecutable(false, false)

            // Configure JSch
            val jsch = JSch().apply {
                addIdentity(tempKeyFile.absolutePath)
            }
            val config = Properties().apply {
                put("StrictHostKeyChecking", "no")
            }
            session = jsch.getSession(user, host, port).apply {
                setConfig(config)
                connect(30000)
            }

            // Open SFTP channel
            sftpChannel = session.openChannel("sftp") as ChannelSftp
            sftpChannel.connect()

            // Execute caller’s block
            val result = block(sftpChannel)

            Result.success(result)
        } catch (e: Exception) {
            Result.failure(e)
        } finally {
            sftpChannel?.disconnect()
            session?.disconnect()
            tempKeyFile?.delete()
        }
    }

    override suspend fun uploadToSCP(
        context: Context,
        dataString: String,
        remoteFilePath: String
    ): Result<Unit> = withSftpSession(context) { sftp ->
        val destFolder = File("$remoteBasePath/$remoteFilePath").parent
        try {
            sftp.stat(destFolder)
        } catch (_: Exception) {
            sftp.mkdir(destFolder)
        }

        val inputStream = ByteArrayInputStream(dataString.toByteArray())
        sftp.put(inputStream, "$remoteBasePath/$remoteFilePath")
    }

    override suspend fun moveFileOnSCP(
        context: Context,
        sourcePath: String,
        destinationPath: String
    ): Result<Unit> = withSftpSession(context) { sftp ->
        // Ensure destination folder exists
        val destFolder = File("$remoteBasePath/$destinationPath").parent
        try {
            sftp.stat(destFolder)
        } catch (_: Exception) {
            sftp.mkdir(destFolder)
        }

        sftp.rename("$remoteBasePath/$sourcePath", "$remoteBasePath/$destinationPath")
    }

    override suspend fun copyFileOnSCP(
        context: Context,
        sourcePath: String,
        destinationPath: String
    ): Result<Unit> = withSftpSession(context) { sftp ->
        // Ensure destination folder exists
        val destFolder = File("$remoteBasePath/$destinationPath").parent
        try {
            sftp.stat(destFolder)
        } catch (_: Exception) {
            sftp.mkdir(destFolder)
        }

        // Download source file into memory
        val outputStream = ByteArrayOutputStream()
        sftp.get("$remoteBasePath/$sourcePath", outputStream)

        // Upload to destination
        val inputStream = ByteArrayInputStream(outputStream.toByteArray())
        sftp.put(inputStream, "$remoteBasePath/$destinationPath")
    }

    override suspend fun downloadOddsFromSCP(
        context: Context,
        remoteFilePath: String
    ): Result<Odds> = withSftpSession(context) { sftp ->
        val outputStream = ByteArrayOutputStream()
        sftp.get("$remoteBasePath/$remoteFilePath", outputStream)

        val jsonString = outputStream.toString(Charsets.UTF_8.name())
        Log.d("Sync from", jsonString)

        val oddsDto = Json.decodeFromString<OddsDTO>(jsonString)
        Log.d("Sync from", oddsDto.toString())

        oddsDto.toOdds()
    }

    private fun writeToFile(data: String, filePath: String) {
        File(filePath).writeText(data)
    }

    private fun getTempFile(context: Context): File {
        return File(context.cacheDir, "temp_file.txt") // 使用缓存目录
    }
}