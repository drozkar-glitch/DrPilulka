package cz.mares.drpilulka

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack

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
