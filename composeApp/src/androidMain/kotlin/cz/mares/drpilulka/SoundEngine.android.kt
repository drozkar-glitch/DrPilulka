package cz.mares.drpilulka

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import java.net.HttpURLConnection
import java.net.URL

actual object SoundPlatform {
    private var musicTrackPlayer: AudioTrack? = null
    private val sfxPlayers = mutableMapOf<SfxType, AudioTrack>()

    private fun getAppContext(): Context? {
        return try {
            val activityThread = Class.forName("android.app.ActivityThread")
            val method = activityThread.getMethod("currentApplication")
            method.invoke(null) as? Context
        } catch (_: Throwable) {
            null
        }
    }

    actual fun saveString(key: String, value: String) {
        try {
            val ctx = getAppContext() ?: return
            val prefs = ctx.getSharedPreferences("drpilulka_prefs", Context.MODE_PRIVATE)
            prefs.edit().putString(key, value).apply()
        } catch (_: Throwable) {
        }
    }

    actual fun loadString(key: String): String? {
        return try {
            val ctx = getAppContext() ?: return null
            val prefs = ctx.getSharedPreferences("drpilulka_prefs", Context.MODE_PRIVATE)
            prefs.getString(key, null)
        } catch (_: Throwable) {
            null
        }
    }

    actual fun httpRequest(
        url: String,
        method: String,
        jsonBody: String?,
        onResult: (statusCode: Int, responseBody: String?) -> Unit
    ) {
        val mainHandler = Handler(Looper.getMainLooper())
        Thread {
            var conn: HttpURLConnection? = null
            try {
                conn = (URL(url).openConnection() as HttpURLConnection).apply {
                    requestMethod = method
                    connectTimeout = 8000
                    readTimeout = 8000
                    setRequestProperty("Accept", "application/json")
                    if (jsonBody != null) {
                        doOutput = true
                        setRequestProperty("Content-Type", "application/json; charset=utf-8")
                        outputStream.use { os ->
                            os.write(jsonBody.toByteArray(Charsets.UTF_8))
                        }
                    }
                }
                val code = conn.responseCode
                val stream = if (code in 200..299) conn.inputStream else conn.errorStream
                val body = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() } ?: ""
                mainHandler.post { onResult(code, body) }
            } catch (_: Throwable) {
                mainHandler.post { onResult(-1, null) }
            } finally {
                conn?.disconnect()
            }
        }.start()
    }

    @Suppress("DEPRECATION")
    actual fun vibrate(sfx: SfxType) {
        try {
            val ctx = getAppContext() ?: return
            val vibrator = ctx.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator ?: return
            if (!vibrator.hasVibrator()) return
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val effect = when (sfx) {
                    SfxType.MOVE -> VibrationEffect.createOneShot(14L, 75)
                    SfxType.ROTATE -> VibrationEffect.createOneShot(18L, 110)
                    SfxType.DROP -> VibrationEffect.createOneShot(32L, 175)
                    SfxType.CLEAR -> VibrationEffect.createWaveform(longArrayOf(0L, 35L, 40L, 55L), -1)
                    SfxType.VIRUS_KILL -> VibrationEffect.createWaveform(longArrayOf(0L, 45L, 35L, 80L), -1)
                    SfxType.COMBO -> VibrationEffect.createWaveform(longArrayOf(0L, 40L, 30L, 55L, 30L, 95L), -1)
                    SfxType.STAGE_CLEAR -> VibrationEffect.createWaveform(longArrayOf(0L, 60L, 45L, 60L, 45L, 140L), -1)
                    SfxType.GAME_OVER -> VibrationEffect.createWaveform(longArrayOf(0L, 120L, 65L, 180L), -1)
                }
                vibrator.vibrate(effect)
            } else {
                val duration = when (sfx) {
                    SfxType.MOVE -> 14L
                    SfxType.ROTATE -> 18L
                    SfxType.DROP -> 32L
                    SfxType.CLEAR -> 65L
                    SfxType.VIRUS_KILL -> 90L
                    SfxType.COMBO -> 120L
                    SfxType.STAGE_CLEAR -> 180L
                    SfxType.GAME_OVER -> 220L
                }
                vibrator.vibrate(duration)
            }
        } catch (_: Throwable) {
        }
    }

    actual fun playLoopingWav(track: MusicTrack, pcmShorts: ShortArray, wavBytes: ByteArray) {
        stopLoopingMusic()
        if (pcmShorts.isEmpty()) return
        try {
            val bufferBytes = pcmShorts.size * 2
            val audioTrack = AudioTrack.Builder()
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_GAME)
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                        .build()
                )
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setSampleRate(RetroSynth.SAMPLE_RATE)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                        .build()
                )
                .setBufferSizeInBytes(bufferBytes)
                .setTransferMode(AudioTrack.MODE_STATIC)
                .build()

            audioTrack.write(pcmShorts, 0, pcmShorts.size)
            audioTrack.setLoopPoints(0, pcmShorts.size, -1)
            audioTrack.play()
            musicTrackPlayer = audioTrack
        } catch (_: Throwable) {
        }
    }

    actual fun stopLoopingMusic() {
        try {
            musicTrackPlayer?.let { player ->
                if (player.playState == AudioTrack.PLAYSTATE_PLAYING) {
                    player.stop()
                }
                player.release()
            }
        } catch (_: Throwable) {
        } finally {
            musicTrackPlayer = null
        }
    }

    actual fun playSfxWav(sfx: SfxType, pcmShorts: ShortArray, wavBytes: ByteArray) {
        if (pcmShorts.isEmpty()) return
        try {
            val existing = sfxPlayers[sfx]
            if (existing != null) {
                if (existing.playState == AudioTrack.PLAYSTATE_PLAYING) {
                    existing.stop()
                }
                existing.reloadStaticData()
                existing.play()
                return
            }
            val bufferBytes = pcmShorts.size * 2
            val sfxTrack = AudioTrack.Builder()
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_GAME)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                )
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setSampleRate(RetroSynth.SAMPLE_RATE)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                        .build()
                )
                .setBufferSizeInBytes(bufferBytes)
                .setTransferMode(AudioTrack.MODE_STATIC)
                .build()

            sfxTrack.write(pcmShorts, 0, pcmShorts.size)
            sfxTrack.play()
            sfxPlayers[sfx] = sfxTrack
        } catch (_: Throwable) {
        }
    }
}
