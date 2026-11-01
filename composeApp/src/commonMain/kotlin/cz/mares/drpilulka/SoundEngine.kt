package cz.mares.drpilulka

import kotlin.math.PI
import kotlin.math.sin

enum class MusicTrack(val label: String) {
    FEVER("FEVER"),
    CHILL("CHILL"),
    OFF("OFF")
}

enum class SfxType {
    MOVE,
    ROTATE,
    DROP,
    CLEAR,
    VIRUS_KILL,
    COMBO,
    STAGE_CLEAR,
    GAME_OVER
}

expect object SoundPlatform {
    fun playLoopingWav(track: MusicTrack, pcmShorts: ShortArray, wavBytes: ByteArray)
    fun stopLoopingMusic()
    fun playSfxWav(sfx: SfxType, pcmShorts: ShortArray, wavBytes: ByteArray)
}

object SoundEngine {
    var soundEnabled: Boolean = true
    private var currentTrack: MusicTrack = MusicTrack.OFF

    private val musicPcmCache = mutableMapOf<MusicTrack, ShortArray>()
    private val musicWavCache = mutableMapOf<MusicTrack, ByteArray>()
    private val sfxPcmCache = mutableMapOf<SfxType, ShortArray>()
    private val sfxWavCache = mutableMapOf<SfxType, ByteArray>()

    fun startMusic(track: MusicTrack) {
        currentTrack = track
        if (!soundEnabled || track == MusicTrack.OFF) {
            SoundPlatform.stopLoopingMusic()
            return
        }
        val pcm = musicPcmCache.getOrPut(track) {
            when (track) {
                MusicTrack.FEVER -> RetroSynth.synthesizeFeverLoop()
                MusicTrack.CHILL -> RetroSynth.synthesizeChillLoop()
                MusicTrack.OFF -> ShortArray(0)
            }
        }
        val wav = musicWavCache.getOrPut(track) {
            RetroSynth.encodeWav(pcm)
        }
        if (pcm.isNotEmpty()) {
            SoundPlatform.playLoopingWav(track, pcm, wav)
        }
    }

    fun stopMusic() {
        SoundPlatform.stopLoopingMusic()
    }

    fun playSfx(sfx: SfxType) {
        if (!soundEnabled) return
        val pcm = sfxPcmCache.getOrPut(sfx) {
            RetroSynth.synthesizeSfx(sfx)
        }
        val wav = sfxWavCache.getOrPut(sfx) {
            RetroSynth.encodeWav(pcm)
        }
        if (pcm.isNotEmpty()) {
            SoundPlatform.playSfxWav(sfx, pcm, wav)
        }
    }
}

object RetroSynth {
    const val SAMPLE_RATE = 11025

    // Frekvence not (Hz)
    private const val REST = 0f
    private const val A2 = 110.00f
    private const val B2 = 123.47f
    private const val C3 = 130.81f
    private const val D3 = 146.83f
    private const val E3 = 164.81f
    private const val F3 = 174.61f
    private const val FS3 = 185.00f
    private const val G3 = 196.00f
    private const val GS3 = 207.65f
    private const val A3 = 220.00f
    private const val B3 = 246.94f
    private const val C4 = 261.63f
    private const val CS4 = 277.18f
    private const val D4 = 293.66f
    private const val DS4 = 311.13f
    private const val E4 = 329.63f
    private const val F4 = 349.23f
    private const val FS4 = 369.99f
    private const val G4 = 392.00f
    private const val GS4 = 415.30f
    private const val A4 = 440.00f
    private const val AS4 = 466.16f
    private const val B4 = 493.88f
    private const val C5 = 523.25f
    private const val CS5 = 554.37f
    private const val D5 = 587.33f
    private const val DS5 = 622.25f
    private const val E5 = 659.25f
    private const val F5 = 698.46f
    private const val FS5 = 739.99f
    private const val G5 = 783.99f
    private const val A5 = 880.00f
    private const val B5 = 987.77f
    private const val C6 = 1046.50f

    // Skladba FEVER (64 kroku po 1/16 taktu, rychlost 125 ms na krok = 8.0 s smycka)
    fun synthesizeFeverLoop(): ShortArray {
        val stepSec = 0.12f
        val leadNotes = floatArrayOf(
            // Takt 1 (Am)
            B4, C5, B4, A4, REST, A4, B4, C5,
            B4, A4, G4, A4, REST, E4, REST, REST,
            // Takt 2 (Am -> E7)
            B4, C5, B4, A4, REST, A4, B4, C5,
            D5, C5, B4, GS4, E4, REST, REST, REST,
            // Takt 3 (Dm -> Am)
            D5, E5, D5, C5, REST, C5, D5, E5,
            D5, C5, B4, C5, A4, REST, E4, REST,
            // Takt 4 (F -> E7)
            F5, E5, D5, C5, B4, A4, GS4, A4,
            B4, REST, E5, REST, A4, REST, REST, REST
        )

        val bassNotes = floatArrayOf(
            // Takt 1
            A2, REST, E3, REST, A3, REST, E3, REST,
            A2, REST, E3, REST, A3, G3, FS3, E3,
            // Takt 2
            A2, REST, E3, REST, A3, REST, E3, REST,
            E3, REST, B2, REST, E3, D3, C3, B2,
            // Takt 3
            D3, REST, A2, REST, D3, REST, A2, REST,
            A2, REST, E3, REST, A3, REST, E3, REST,
            // Takt 4
            F3, REST, C3, REST, D3, REST, A2, REST,
            E3, REST, B2, REST, A2, REST, E3, REST
        )

        return renderChiptunePattern(leadNotes, bassNotes, stepSec, dutyCycle = 0.25f)
    }

    // Skladba CHILL (64 kroku po 1/16 taktu, rychlost 130 ms na krok)
    fun synthesizeChillLoop(): ShortArray {
        val stepSec = 0.13f
        val leadNotes = floatArrayOf(
            // Takt 1 (C -> G)
            E5, REST, G5, E5, REST, C5, D5, DS5,
            E5, REST, C5, A4, G4, REST, REST, REST,
            // Takt 2 (F -> G)
            F5, REST, A5, F5, REST, D5, E5, F5,
            G5, REST, E5, C5, D5, REST, G4, REST,
            // Takt 3 (C -> Am)
            E5, REST, G5, E5, REST, C5, D5, E5,
            A5, G5, E5, C5, D5, C5, A4, REST,
            // Takt 4 (Dm -> G7 -> C)
            D5, E5, F5, D5, B4, C5, D5, B4,
            C5, REST, G5, REST, C5, REST, REST, REST
        )

        val bassNotes = floatArrayOf(
            // Takt 1
            C3, REST, G3, REST, C3, REST, G3, REST,
            C3, REST, G3, REST, G3, F3, E3, D3,
            // Takt 2
            F3, REST, C3, REST, F3, REST, C3, REST,
            G3, REST, D3, REST, G3, REST, D3, REST,
            // Takt 3
            C3, REST, G3, REST, C3, REST, G3, REST,
            A2, REST, E3, REST, A2, REST, E3, REST,
            // Takt 4
            D3, REST, A2, REST, G3, REST, D3, REST,
            C3, REST, G3, REST, C3, REST, G2_FREQ, REST
        )

        return renderChiptunePattern(leadNotes, bassNotes, stepSec, dutyCycle = 0.50f)
    }

    private const val G2_FREQ = 98.00f

    private fun renderChiptunePattern(
        leadNotes: FloatArray,
        bassNotes: FloatArray,
        stepSec: Float,
        dutyCycle: Float
    ): ShortArray {
        val stepSamples = (SAMPLE_RATE * stepSec).toInt()
        val totalSamples = leadNotes.size * stepSamples
        val output = ShortArray(totalSamples)

        var leadPhase = 0f
        var bassPhase = 0f
        var lfsr = 0x7FFF

        for (step in leadNotes.indices) {
            val lFreq = leadNotes[step]
            val bFreq = bassNotes[step]
            val startIdx = step * stepSamples
            val isHiHat = (step % 2 == 1)
            val isSnare = (step % 8 == 4)

            for (i in 0 until stepSamples) {
                val progress = i.toFloat() / stepSamples.toFloat()

                // Kanal 1: Pulse / Square vlna (hlavni melodie)
                var leadSample = 0f
                if (lFreq > 0f) {
                    leadPhase = (leadPhase + lFreq / SAMPLE_RATE) % 1.0f
                    val rawPulse = if (leadPhase < dutyCycle) 1f else -1f
                    val env = (1f - progress * 0.45f).coerceAtLeast(0.15f)
                    leadSample = rawPulse * env * 0.32f
                }

                // Kanal 2: Triangle vlna (8bitova basa)
                var bassSample = 0f
                if (bFreq > 0f) {
                    bassPhase = (bassPhase + bFreq / SAMPLE_RATE) % 1.0f
                    val rawTri = if (bassPhase < 0.5f) {
                        (bassPhase * 4f) - 1f
                    } else {
                        3f - (bassPhase * 4f)
                    }
                    val env = (1f - progress * 0.35f).coerceAtLeast(0.2f)
                    bassSample = rawTri * env * 0.36f
                }

                // Kanal 3: Lehky 8bitovy bicak / hi-hat sum
                var noiseSample = 0f
                if (isSnare && i < stepSamples / 3) {
                    val bit = ((lfsr shr 0) xor (lfsr shr 1)) and 1
                    lfsr = (lfsr shr 1) or (bit shl 14)
                    val nVal = if ((lfsr and 1) != 0) 1f else -1f
                    val nEnv = 1f - (i.toFloat() / (stepSamples / 3f))
                    noiseSample = nVal * nEnv * 0.14f
                } else if (isHiHat && i < stepSamples / 6) {
                    val bit = ((lfsr shr 0) xor (lfsr shr 1)) and 1
                    lfsr = (lfsr shr 1) or (bit shl 14)
                    val nVal = if ((lfsr and 1) != 0) 1f else -1f
                    val nEnv = 1f - (i.toFloat() / (stepSamples / 6f))
                    noiseSample = nVal * nEnv * 0.07f
                }

                val mixed = (leadSample + bassSample + noiseSample).coerceIn(-0.95f, 0.95f)
                output[startIdx + i] = (mixed * 32767f).toInt().toShort()
            }
        }
        return output
    }

    fun synthesizeSfx(sfx: SfxType): ShortArray {
        return when (sfx) {
            SfxType.MOVE -> generateToneSequence(
                freqs = floatArrayOf(C5),
                durationsMs = intArrayOf(35),
                duty = 0.25f,
                volume = 0.35f
            )
            SfxType.ROTATE -> generateToneSequence(
                freqs = floatArrayOf(E5, G5),
                durationsMs = intArrayOf(30, 40),
                duty = 0.50f,
                volume = 0.40f
            )
            SfxType.DROP -> generateToneSequence(
                freqs = floatArrayOf(A3, E3),
                durationsMs = intArrayOf(35, 55),
                duty = 0.50f,
                volume = 0.45f
            )
            SfxType.CLEAR -> generateToneSequence(
                freqs = floatArrayOf(C5, E5, G5, C6),
                durationsMs = intArrayOf(45, 45, 45, 90),
                duty = 0.25f,
                volume = 0.48f
            )
            SfxType.VIRUS_KILL -> generateToneSequence(
                freqs = floatArrayOf(G5, DS5, B4, G5, B5, C6),
                durationsMs = intArrayOf(40, 40, 40, 50, 50, 110),
                duty = 0.25f,
                volume = 0.52f
            )
            SfxType.COMBO -> generateToneSequence(
                freqs = floatArrayOf(E5, G5, B5, E5, G5, B5, C6),
                durationsMs = intArrayOf(40, 40, 40, 40, 40, 40, 140),
                duty = 0.50f,
                volume = 0.52f
            )
            SfxType.STAGE_CLEAR -> generateToneSequence(
                freqs = floatArrayOf(C5, D5, E5, G5, E5, G5, C6),
                durationsMs = intArrayOf(90, 90, 90, 140, 90, 120, 320),
                duty = 0.50f,
                volume = 0.55f
            )
            SfxType.GAME_OVER -> generateToneSequence(
                freqs = floatArrayOf(E5, DS5, D5, CS5, C5, B4, A4),
                durationsMs = intArrayOf(110, 110, 110, 110, 130, 150, 340),
                duty = 0.25f,
                volume = 0.50f
            )
        }
    }

    private fun generateToneSequence(
        freqs: FloatArray,
        durationsMs: IntArray,
        duty: Float,
        volume: Float
    ): ShortArray {
        var totalSamples = 0
        val sampleCounts = IntArray(freqs.size)
        for (i in freqs.indices) {
            val count = (SAMPLE_RATE * (durationsMs[i] / 1000f)).toInt().coerceAtLeast(1)
            sampleCounts[i] = count
            totalSamples += count
        }

        val out = ShortArray(totalSamples)
        var cursor = 0
        var phase = 0f

        for (i in freqs.indices) {
            val freq = freqs[i]
            val count = sampleCounts[i]
            for (s in 0 until count) {
                val env = (1f - (s.toFloat() / count.toFloat()) * 0.5f).coerceAtLeast(0.1f)
                val sample = if (freq > 0f) {
                    phase = (phase + freq / SAMPLE_RATE) % 1.0f
                    val raw = if (phase < duty) 1f else -1f
                    raw * env * volume
                } else {
                    0f
                }
                out[cursor++] = (sample * 32767f).toInt().toShort()
            }
        }
        return out
    }

    // Zabaleni 16bitovych PCM vzorku do standardniho RIFF WAV souboru v pameti
    fun encodeWav(pcm: ShortArray): ByteArray {
        val dataSize = pcm.size * 2
        val totalSize = 44 + dataSize
        val bytes = ByteArray(totalSize)

        fun writeAscii(offset: Int, text: String) {
            for (i in text.indices) {
                bytes[offset + i] = text[i].code.toByte()
            }
        }

        fun writeIntLE(offset: Int, value: Int) {
            bytes[offset] = (value and 0xFF).toByte()
            bytes[offset + 1] = ((value shr 8) and 0xFF).toByte()
            bytes[offset + 2] = ((value shr 16) and 0xFF).toByte()
            bytes[offset + 3] = ((value shr 24) and 0xFF).toByte()
        }

        fun writeShortLE(offset: Int, value: Int) {
            bytes[offset] = (value and 0xFF).toByte()
            bytes[offset + 1] = ((value shr 8) and 0xFF).toByte()
        }

        writeAscii(0, "RIFF")
        writeIntLE(4, totalSize - 8)
        writeAscii(8, "WAVE")
        writeAscii(12, "fmt ")
        writeIntLE(16, 16) // PCM chunk size
        writeShortLE(20, 1) // AudioFormat 1 = PCM
        writeShortLE(22, 1) // 1 kanal (Mono)
        writeIntLE(24, SAMPLE_RATE)
        writeIntLE(28, SAMPLE_RATE * 2) // ByteRate
        writeShortLE(32, 2) // BlockAlign
        writeShortLE(34, 16) // BitsPerSample
        writeAscii(36, "data")
        writeIntLE(40, dataSize)

        var pos = 44
        for (s in pcm) {
            val v = s.toInt()
            bytes[pos++] = (v and 0xFF).toByte()
            bytes[pos++] = ((v shr 8) and 0xFF).toByte()
        }
        return bytes
    }

    private const val BASE64_CHARS = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/"

    fun toBase64(bytes: ByteArray): String {
        val sb = StringBuilder(((bytes.size + 2) / 3) * 4)
        var i = 0
        while (i < bytes.size) {
            val b0 = bytes[i].toInt() and 0xFF
            val b1 = if (i + 1 < bytes.size) bytes[i + 1].toInt() and 0xFF else 0
            val b2 = if (i + 2 < bytes.size) bytes[i + 2].toInt() and 0xFF else 0

            val triple = (b0 shl 16) or (b1 shl 8) or b2
            sb.append(BASE64_CHARS[(triple shr 18) and 0x3F])
            sb.append(BASE64_CHARS[(triple shr 12) and 0x3F])
            sb.append(if (i + 1 < bytes.size) BASE64_CHARS[(triple shr 6) and 0x3F] else '=')
            sb.append(if (i + 2 < bytes.size) BASE64_CHARS[triple and 0x3F] else '=')
            i += 3
        }
        return sb.toString()
    }
}
