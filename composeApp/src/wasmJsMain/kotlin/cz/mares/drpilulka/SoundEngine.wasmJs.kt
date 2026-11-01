package cz.mares.drpilulka

private fun jsPlayWavBase64(base64Wav: String, loop: Boolean, isMusic: Boolean): Unit = js("""
(function() {
    try {
        if (!window._drPilulkaAudio) {
            window._drPilulkaAudio = { bgm: null };
        }
        if (isMusic) {
            if (window._drPilulkaAudio.bgm) {
                window._drPilulkaAudio.bgm.pause();
                window._drPilulkaAudio.bgm = null;
            }
            var bgm = new Audio("data:audio/wav;base64," + base64Wav);
            bgm.loop = loop;
            bgm.volume = 0.55;
            bgm.play().catch(function() {});
            window._drPilulkaAudio.bgm = bgm;
        } else {
            var sfx = new Audio("data:audio/wav;base64," + base64Wav);
            sfx.volume = 0.80;
            sfx.play().catch(function() {});
        }
    } catch (e) {}
})()
""")

private fun jsStopMusic(): Unit = js("""
(function() {
    try {
        if (window._drPilulkaAudio && window._drPilulkaAudio.bgm) {
            window._drPilulkaAudio.bgm.pause();
            window._drPilulkaAudio.bgm = null;
        }
    } catch (e) {}
})()
""")

private fun jsVibrate(ms: Int): Unit = js("""
(function() {
    try {
        if (navigator && navigator.vibrate) {
            navigator.vibrate(ms);
        }
    } catch (e) {}
})()
""")

private fun jsSavePref(key: String, value: String): Unit = js("""
(function() {
    try {
        window.localStorage.setItem(key, value);
    } catch (e) {}
})()
""")

private fun jsLoadPref(key: String): String = js("""
(function() {
    try {
        var v = window.localStorage.getItem(key);
        return v !== null ? v : "";
    } catch (e) {
        return "";
    }
})()
""")

actual object SoundPlatform {
    private val musicBase64Cache = mutableMapOf<MusicTrack, String>()
    private val sfxBase64Cache = mutableMapOf<SfxType, String>()

    actual fun saveString(key: String, value: String) {
        jsSavePref(key, value)
    }

    actual fun loadString(key: String): String? {
        val res = jsLoadPref(key)
        return if (res.isEmpty()) null else res
    }

    actual fun vibrate(sfx: SfxType) {
        val duration = when (sfx) {
            SfxType.MOVE -> 12
            SfxType.ROTATE -> 16
            SfxType.DROP -> 28
            SfxType.CLEAR -> 55
            SfxType.VIRUS_KILL -> 75
            SfxType.COMBO -> 100
            SfxType.STAGE_CLEAR -> 150
            SfxType.GAME_OVER -> 200
        }
        jsVibrate(duration)
    }

    actual fun playLoopingWav(track: MusicTrack, pcmShorts: ShortArray, wavBytes: ByteArray) {
        if (wavBytes.isEmpty()) return
        val b64 = musicBase64Cache.getOrPut(track) { RetroSynth.toBase64(wavBytes) }
        jsPlayWavBase64(b64, loop = true, isMusic = true)
    }

    actual fun stopLoopingMusic() {
        jsStopMusic()
    }

    actual fun playSfxWav(sfx: SfxType, pcmShorts: ShortArray, wavBytes: ByteArray) {
        if (wavBytes.isEmpty()) return
        val b64 = sfxBase64Cache.getOrPut(sfx) { RetroSynth.toBase64(wavBytes) }
        jsPlayWavBase64(b64, loop = false, isMusic = false)
    }
}
