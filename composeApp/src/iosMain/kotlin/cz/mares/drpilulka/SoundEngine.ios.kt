package cz.mares.drpilulka

import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
import platform.AVFAudio.AVAudioPlayer
import platform.AVFAudio.AVAudioSession
import platform.AVFAudio.AVAudioSessionCategoryAmbient
import platform.AVFAudio.setActive
import platform.Foundation.NSData
import platform.Foundation.NSUserDefaults
import platform.Foundation.create

@OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)
actual object SoundPlatform {
    private var musicPlayer: AVAudioPlayer? = null
    private val sfxPlayers = mutableMapOf<SfxType, AVAudioPlayer>()
    private var sessionConfigured = false

    actual fun saveString(key: String, value: String) {
        try {
            NSUserDefaults.standardUserDefaults.setObject(value, forKey = key)
        } catch (_: Throwable) {
        }
    }

    actual fun loadString(key: String): String? {
        return try {
            NSUserDefaults.standardUserDefaults.stringForKey(key)
        } catch (_: Throwable) {
            null
        }
    }

    private fun ensureSession() {
        if (!sessionConfigured) {
            try {
                val session = AVAudioSession.sharedInstance()
                session.setCategory(AVAudioSessionCategoryAmbient, error = null)
                session.setActive(true, error = null)
                sessionConfigured = true
            } catch (_: Throwable) {
            }
        }
    }

    private fun ByteArray.toNSData(): NSData = this.usePinned { pinned ->
        NSData.create(bytes = pinned.addressOf(0), length = this.size.toULong())
    }

    actual fun playLoopingWav(track: MusicTrack, pcmShorts: ShortArray, wavBytes: ByteArray) {
        stopLoopingMusic()
        if (wavBytes.isEmpty()) return
        ensureSession()
        try {
            val data = wavBytes.toNSData()
            val player = AVAudioPlayer(data = data, error = null)
            player.numberOfLoops = -1
            player.volume = 0.55f
            player.prepareToPlay()
            player.play()
            musicPlayer = player
        } catch (_: Throwable) {
        }
    }

    actual fun stopLoopingMusic() {
        try {
            musicPlayer?.stop()
        } catch (_: Throwable) {
        } finally {
            musicPlayer = null
        }
    }

    actual fun playSfxWav(sfx: SfxType, pcmShorts: ShortArray, wavBytes: ByteArray) {
        if (wavBytes.isEmpty()) return
        ensureSession()
        try {
            val existing = sfxPlayers[sfx]
            if (existing != null) {
                existing.currentTime = 0.0
                existing.play()
                return
            }
            val data = wavBytes.toNSData()
            val player = AVAudioPlayer(data = data, error = null)
            player.numberOfLoops = 0
            player.volume = 0.80f
            player.prepareToPlay()
            player.play()
            sfxPlayers[sfx] = player
        } catch (_: Throwable) {
        }
    }
}
