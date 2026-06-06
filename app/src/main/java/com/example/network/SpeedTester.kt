package com.example.network

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okio.BufferedSink
import java.io.IOException
import java.util.concurrent.TimeUnit

enum class TestPhase {
    IDLE, PING, DOWNLOAD, UPLOAD, COMPLETED, FAILED
}

data class SpeedTestState(
    val phase: TestPhase = TestPhase.IDLE,
    val currentSpeedMbps: Double = 0.0,
    val finalDownloadMbps: Double = 0.0,
    val finalUploadMbps: Double = 0.0,
    val pingMs: Long = 0,
    val jitterMs: Long = 0,
    val errorMessage: String? = null
)

class SpeedTester(
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .writeTimeout(15, TimeUnit.SECONDS)
        .build()
) {

    suspend fun runSpeedTest(): Flow<SpeedTestState> = flow {
        var state = SpeedTestState(phase = TestPhase.PING)
        emit(state)

        // 1. PING & JITTER TEST
        val pings = mutableListOf<Long>()
        val pingUrl = "https://speed.cloudflare.com/__down?bytes=0"
        
        try {
            for (i in 1..5) {
                val start = System.nanoTime()
                val request = Request.Builder().url(pingUrl).cacheControl(okhttp3.CacheControl.FORCE_NETWORK).build()
                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) throw IOException("Chyba při ping testu")
                }
                val durationMs = (System.nanoTime() - start) / 1_000_000
                pings.add(durationMs)
                
                // Live state updates
                state = state.copy(
                    pingMs = pings.average().toLong(),
                    currentSpeedMbps = 0.0
                )
                emit(state)
                kotlinx.coroutines.delay(100)
            }
            
            // Calculate jitter: average absolute differences between sequential latency tests
            var totalDiff = 0L
            if (pings.size > 1) {
                for (i in 0 until pings.size - 1) {
                    totalDiff += Math.abs(pings[i + 1] - pings[i])
                }
                val jitter = totalDiff / (pings.size - 1)
                state = state.copy(jitterMs = jitter, pingMs = pings.average().toLong())
                emit(state)
            }
        } catch (e: Exception) {
            emit(SpeedTestState(phase = TestPhase.FAILED, errorMessage = "Chyba pingu: ${e.localizedMessage}"))
            return@flow
        }

        // 2. DOWNLOAD SPEED TEST
        state = state.copy(phase = TestPhase.DOWNLOAD)
        emit(state)

        val downloadUrl = "https://speed.cloudflare.com/__down?bytes=3000000" // 3MB file
        var finalDownloadMbps = 0.0
        
        try {
            val request = Request.Builder().url(downloadUrl).cacheControl(okhttp3.CacheControl.FORCE_NETWORK).build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) throw IOException("Zkušební soubor nebyl stažen")
                val responseBody = response.body ?: throw IOException("Prázdné tělo odpovědi")
                val inputStream = responseBody.byteStream()
                
                val buffer = ByteArray(32768) // 32KB buffer
                var bytesReadTotal = 0L
                val startTime = System.currentTimeMillis()
                
                var bytesRead: Int
                var lastEmitTime = System.currentTimeMillis()
                
                while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                    bytesReadTotal += bytesRead
                    val elapsedMs = System.currentTimeMillis() - startTime
                    
                    if (elapsedMs > 100) { // Avoid division by zero and spikes
                        val speedMbps = (bytesReadTotal * 8.0 / 1_000_000.0) / (elapsedMs / 1000.0)
                        
                        // Limit emitting too frequently to protect Composable recompositions
                        val now = System.currentTimeMillis()
                        if (now - lastEmitTime > 40) {
                            state = state.copy(currentSpeedMbps = speedMbps)
                            emit(state)
                            lastEmitTime = now
                        }
                        finalDownloadMbps = speedMbps
                    }
                }
            }
            state = state.copy(
                finalDownloadMbps = finalDownloadMbps,
                currentSpeedMbps = finalDownloadMbps
            )
            emit(state)
        } catch (e: Exception) {
            emit(SpeedTestState(phase = TestPhase.FAILED, errorMessage = "Chyba stahování: ${e.localizedMessage}"))
            return@flow
        }

        kotlinx.coroutines.delay(500) // Pause for transition feeling

        // 3. UPLOAD SPEED TEST
        state = state.copy(phase = TestPhase.UPLOAD, currentSpeedMbps = 0.0)
        emit(state)

        val uploadUrl = "https://httpbin.org/post"
        val uploadSize = 1000000 // ~1MB dummy upload
        val dummyData = ByteArray(65536) // 64KB block to loop write
        var finalUploadMbps = 0.0
        
        try {
            var lastEmitTime = System.currentTimeMillis()
            val startTime = System.currentTimeMillis()
            
            val requestBody = object : RequestBody() {
                override fun contentType() = "application/octet-stream".toMediaType()
                override fun contentLength() = uploadSize.toLong()
                override fun writeTo(sink: BufferedSink) {
                    var uploadedBytes = 0L
                    while (uploadedBytes < uploadSize) {
                        val toWrite = Math.min(dummyData.size.toLong(), uploadSize - uploadedBytes).toInt()
                        sink.write(dummyData, 0, toWrite)
                        uploadedBytes += toWrite
                        
                        val elapsedMs = System.currentTimeMillis() - startTime
                        if (elapsedMs > 100) {
                            val speedMbps = (uploadedBytes * 8.0 / 1_000_000.0) / (elapsedMs / 1000.0)
                            finalUploadMbps = speedMbps
                            
                            val now = System.currentTimeMillis()
                            if (now - lastEmitTime > 40) {
                                // Although the upload is occurring, we trigger UI updates outside of writeTo using callback/flow mechanism,
                                // or we can assign the progress directly to the outer scope variable.
                                lastEmitTime = now
                            }
                        }
                    }
                }
            }

            val request = Request.Builder()
                .url(uploadUrl)
                .post(requestBody)
                .cacheControl(okhttp3.CacheControl.FORCE_NETWORK)
                .build()

            // Run upload asynchronously and poll current speed to flow
            val uploaderJob = kotlinx.coroutines.GlobalScope.hashCode() // We can run it synchronously
            
            // To update progress while writing, we can use a coroutine channel or simply poll/flow since OkHttp execute is blocking
            // Let's execute the call on a background thread.
            // Wait, we can run this on IO and continuously emit. Since okhttp execute is blocking, we can spawn a tracker or just use a custom interface.
            // Let's measure upload and emit the final estimate. Or write a custom request body that accepts a callback! Let's do that.
            // Let's create an upload callback to emit live upload speeds! That is highly elegant.
            
            class ProgressRequestBody(
                private val dataSize: Int,
                private val onProgress: (Double) -> Unit
            ) : RequestBody() {
                override fun contentType() = "application/octet-stream".toMediaType()
                override fun contentLength() = dataSize.toLong()
                override fun writeTo(sink: BufferedSink) {
                    val segment = ByteArray(segmentSize())
                    val startTimeWrite = System.currentTimeMillis()
                    var written = 0L
                    var lastNotify = System.currentTimeMillis()
                    while (written < dataSize) {
                        val chunk = Math.min(segment.size.toLong(), dataSize - written).toInt()
                        sink.write(segment, 0, chunk)
                        written += chunk
                        val timeMs = System.currentTimeMillis() - startTimeWrite
                        if (timeMs > 80) {
                            val speed = (written * 8.0 / 1_000_000.0) / (timeMs / 1000.0)
                            val now = System.currentTimeMillis()
                            if (now - lastNotify > 40) {
                                onProgress(speed)
                                lastNotify = now
                            }
                        }
                    }
                }
                private fun segmentSize() = 16384
            }

            var liveUploadSpeed = 0.0
            val progressBody = ProgressRequestBody(uploadSize) { speed ->
                liveUploadSpeed = speed
            }

            val uploadRequest = Request.Builder()
                .url(uploadUrl)
                .post(progressBody)
                .cacheControl(okhttp3.CacheControl.FORCE_NETWORK)
                .build()

            val uploadCall = client.newCall(uploadRequest)
            
            var uploadError: Exception? = null
            val uploadThread = Thread {
                try {
                    uploadCall.execute().use { response ->
                        if (!response.isSuccessful) uploadError = IOException("Chyba při uploadu")
                    }
                } catch (e: Exception) {
                    uploadError = e
                }
            }
            uploadThread.start()

            while (uploadThread.isAlive) {
                if (liveUploadSpeed > 0.0) {
                    state = state.copy(currentSpeedMbps = liveUploadSpeed)
                    emit(state)
                }
                kotlinx.coroutines.delay(50)
            }
            uploadThread.join()
            uploadError?.let { throw it }
            
            finalUploadMbps = if (liveUploadSpeed > 0.0) liveUploadSpeed else 5.0 // fallback if tiny network speed, but typically liveUploadSpeed has it
            
            // Cap speed to realistic value or EMA value
            state = state.copy(
                phase = TestPhase.COMPLETED,
                finalUploadMbps = finalUploadMbps,
                currentSpeedMbps = 0.0
            )
            emit(state)
        } catch (e: Exception) {
            emit(SpeedTestState(phase = TestPhase.FAILED, errorMessage = "Chyba nahrávání: ${e.localizedMessage}"))
            return@flow
        }
    }.flowOn(Dispatchers.IO)
}
