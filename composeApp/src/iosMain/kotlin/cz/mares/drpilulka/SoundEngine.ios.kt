package cz.mares.drpilulka

import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.readBytes
import kotlinx.cinterop.usePinned
import platform.AVFAudio.AVAudioPlayer
import platform.AVFAudio.AVAudioSession
import platform.AVFAudio.AVAudioSessionCategoryAmbient
import platform.AVFAudio.setActive
import platform.Foundation.NSData
import platform.Foundation.NSHTTPURLResponse
import platform.Foundation.NSMutableURLRequest
import platform.Foundation.NSURL
import platform.Foundation.NSURLSession
import platform.Foundation.NSUserDefaults
import platform.Foundation.create
import platform.Foundation.dataTaskWithRequest
import platform.Foundation.setHTTPBody
import platform.Foundation.setHTTPMethod
import platform.Foundation.setValue
import platform.UIKit.UIImpactFeedbackGenerator
import platform.UIKit.UIImpactFeedbackStyle
import platform.UIKit.UINotificationFeedbackGenerator
import platform.UIKit.UINotificationFeedbackType
import platform.darwin.dispatch_async
import platform.darwin.dispatch_get_main_queue

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

    actual fun httpRequest(
        url: String,
        method: String,
        jsonBody: String?,
        onResult: (statusCode: Int, responseBody: String?) -> Unit
    ) {
        try {
            val nsUrl = NSURL.URLWithString(url)
            if (nsUrl == null) {
                dispatch_async(dispatch_get_main_queue()) {
                    onResult(-1, null)
                }
                return
            }
            val request = NSMutableURLRequest.requestWithURL(nsUrl)
            request.setHTTPMethod(method)
            request.setValue("application/json", forHTTPHeaderField = "Accept")
            if (jsonBody != null) {
                request.setValue("application/json; charset=utf-8", forHTTPHeaderField = "Content-Type")
                request.setHTTPBody(jsonBody.encodeToByteArray().toNSData())
            }
            val task = NSURLSession.sharedSession.dataTaskWithRequest(request) { data, response, error ->
                val httpResp = response as? NSHTTPURLResponse
                val statusCode = if (error != null) -1 else (httpResp?.statusCode?.toInt() ?: 200)
                val bodyStr = if (data != null) {
                    val len = data.length.toInt()
                    val ptr = data.bytes
                    if (len > 0 && ptr != null) ptr.readBytes(len).decodeToString() else ""
                } else {
                    null
                }
                dispatch_async(dispatch_get_main_queue()) {
                    onResult(statusCode, bodyStr)
                }
            }
            task.resume()
        } catch (_: Throwable) {
            dispatch_async(dispatch_get_main_queue()) {
                onResult(-1, null)
            }
        }
    }

    actual fun vibrate(sfx: SfxType) {
        try {
            when (sfx) {
                SfxType.MOVE -> {
                    val gen = UIImpactFeedbackGenerator(style = UIImpactFeedbackStyle.UIImpactFeedbackStyleLight)
                    gen.prepare()
                    gen.impactOccurred()
                }
                SfxType.ROTATE, SfxType.DROP -> {
                    val gen = UIImpactFeedbackGenerator(style = UIImpactFeedbackStyle.UIImpactFeedbackStyleMedium)
                    gen.prepare()
                    gen.impactOccurred()
                }
                SfxType.CLEAR, SfxType.VIRUS_KILL -> {
                    val gen = UIImpactFeedbackGenerator(style = UIImpactFeedbackStyle.UIImpactFeedbackStyleHeavy)
                    gen.prepare()
                    gen.impactOccurred()
                }
                SfxType.COMBO, SfxType.STAGE_CLEAR -> {
                    val gen = UINotificationFeedbackGenerator()
                    gen.prepare()
                    gen.notificationOccurred(UINotificationFeedbackType.UINotificationFeedbackTypeSuccess)
                }
                SfxType.GAME_OVER -> {
                    val gen = UINotificationFeedbackGenerator()
                    gen.prepare()
                    gen.notificationOccurred(UINotificationFeedbackType.UINotificationFeedbackTypeError)
                }
            }
        } catch (_: Throwable) {
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
