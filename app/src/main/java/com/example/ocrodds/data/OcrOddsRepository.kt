package com.example.ocrodds.data

import android.annotation.SuppressLint
import android.content.Context
import com.jcraft.jsch.ChannelSftp
import com.jcraft.jsch.JSch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayInputStream
import java.io.File
import java.util.Properties

interface OcrOddsRepository {
    suspend fun uploadToSCP(
        context: Context,
        dataString: String,
        dateFolder: String,
        filename: String
    ): Result<Unit>
}

class DefaultOcrOddsRepository(
    private val host: String,
    private val port: Int,
    private val user: String,
    private val remoteBasePath: String,
    private val keyFileAssetsName: String
) : OcrOddsRepository {
    @SuppressLint("SetWorldReadable", "SetWorldWritable")
    override suspend fun uploadToSCP(
        context: Context,
        dataString: String,
        dateFolder: String,
        filename: String
    ): Result<Unit> = withContext(Dispatchers.IO) {
        try {

            // Copy id_rsa from assets to a temporary file
            val tempKeyFile = File(context.filesDir, "id_rsa_temp")
            context.assets.open(keyFileAssetsName).use { input ->
                tempKeyFile.outputStream().use { output ->
                    input.copyTo(output)
                }
            }
            // Set appropriate permissions for the key file
            tempKeyFile.setReadable(true, false)
            tempKeyFile.setWritable(true, false)
            tempKeyFile.setExecutable(false, false)

            // Configure JSch
            val jsch = JSch()
            jsch.addIdentity(tempKeyFile.absolutePath)
            val config = Properties().apply {
                put("StrictHostKeyChecking", "no")
            }
            val session = jsch.getSession(user, host, port).apply {
                setConfig(config)
                connect(30000) // 30s timeout
            }
            val remoteFolderPath = "$remoteBasePath/$dateFolder"

            // Create directory if it doesn't exist
            val sftpChannel = session.openChannel("sftp") as ChannelSftp
            sftpChannel.connect()
            try {
                sftpChannel.stat(remoteFolderPath)
            } catch (e: Exception) {
                sftpChannel.mkdir(remoteFolderPath)
            }

            // Upload file
            val remoteFilePath = "$remoteFolderPath/$filename"
            val inputStream = ByteArrayInputStream(dataString.toByteArray())
            sftpChannel.put(inputStream, remoteFilePath)
            sftpChannel.disconnect()

            session.disconnect()

            // Clean up temporary file
            tempKeyFile.delete()

            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun writeToFile(data: String, filePath: String) {
        File(filePath).writeText(data)
    }

    private fun getTempFile(context: Context): File {
        return File(context.cacheDir, "temp_file.txt") // 使用缓存目录
    }
}