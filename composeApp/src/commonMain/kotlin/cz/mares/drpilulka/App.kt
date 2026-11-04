package cz.mares.drpilulka

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlin.random.Random

private const val GRID_COLS = 8
private const val GRID_ROWS = 16

enum class PillColor(val mainHex: Color, val lightHex: Color, val darkHex: Color) {
    RED(Color(0xFFEF4444), Color(0xFFFCA5A5), Color(0xFF991B1B)),
    YELLOW(Color(0xFFFACC15), Color(0xFFFEF08A), Color(0xFFA16207)),
    BLUE(Color(0xFF3B82F6), Color(0xFF93C5FD), Color(0xFF1E40AF))
}

enum class CellKind {
    EMPTY,
    VIRUS,
    SINGLE,
    LEFT,
    RIGHT,
    TOP,
    BOTTOM,
    CLEARING
}

data class GridCell(
    val kind: CellKind = CellKind.EMPTY,
    val color: PillColor? = null
)

enum class SpeedLevel(val label: String, val shortLabel: String, val baseDelayMs: Long) {
    LOW("POMALÁ", "POM", 1050L),
    MED("STŘEDNÍ", "STŘ", 750L),
    HI("RYCHLÁ", "RYC", 480L)
}

enum class ScreenMode {
    MENU,
    PLAYING,
    LEADERBOARD
}

enum class PlayPhase {
    FALLING,
    CLEARING_ANIM,
    GRAVITY_STEP,
    STAGE_CLEAR,
    GAME_OVER,
    PAUSED
}

data class ActiveCapsule(
    val x: Int,
    val y: Int,
    val isVertical: Boolean,
    val colorA: PillColor, // Vodorovne: leva pulka, Svisle: spodni pulka (pivot)
    val colorB: PillColor  // Vodorovne: prava pulka, Svisle: horni pulka
)

data class SavedGameState(
    val level: Int,
    val score: Int,
    val pillsThrown: Int,
    val speed: SpeedLevel,
    val music: MusicTrack,
    val nextColorA: PillColor,
    val nextColorB: PillColor,
    val activeCapsule: ActiveCapsule?,
    val board: Array<Array<GridCell>>
)

@Composable
fun App() {
    MaterialTheme {
        var screenMode by remember { mutableStateOf(ScreenMode.MENU) }
        var selectedLevel by remember { mutableIntStateOf(GameStorage.loadLastLevel()) }
        var selectedSpeed by remember {
            mutableStateOf(
                SpeedLevel.entries.find { it.name == GameStorage.loadLastSpeedName() } ?: SpeedLevel.MED
            )
        }
        var selectedMusic by remember {
            mutableStateOf(
                MusicTrack.entries.find { it.name == GameStorage.loadLastMusicName() } ?: MusicTrack.FEVER
            )
        }
        var soundMuted by remember {
            val muted = GameStorage.loadMuted()
            SoundEngine.soundEnabled = !muted
            mutableStateOf(muted)
        }
        var vibrationMuted by remember {
            val vMuted = GameStorage.loadVibrationMuted()
            SoundEngine.vibrationEnabled = !vMuted
            mutableStateOf(vMuted)
        }
        var topScore by remember { mutableIntStateOf(GameStorage.loadTopScore()) }
        var playerNick by remember { mutableStateOf(GameStorage.loadPlayerNick()) }
        var savedGame by remember { mutableStateOf(deserializeSavedGame(GameStorage.loadGameData())) }
        var resumeFromSaved by remember { mutableStateOf(false) }

        DisposableEffect(Unit) {
            onDispose {
                SoundEngine.stopMusic()
            }
        }

        Surface(
            modifier = Modifier.fillMaxSize(),
            color = Color(0xFF070A14)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(0xFF070A14))
                    .windowInsetsPadding(WindowInsets.safeDrawing)
            ) {
                when (screenMode) {
                    ScreenMode.MENU -> {
                        RetroMenuScreen(
                            level = selectedLevel,
                            speed = selectedSpeed,
                            music = selectedMusic,
                            soundMuted = soundMuted,
                            vibrationMuted = vibrationMuted,
                            topScore = topScore,
                            playerNick = playerNick,
                            savedGame = savedGame,
                            onLevelChange = { newLvl ->
                                val clamped = newLvl.coerceIn(0, 20)
                                selectedLevel = clamped
                                GameStorage.saveLastLevel(clamped)
                                SoundEngine.playSfx(SfxType.MOVE)
                            },
                            onSpeedChange = { newSpd ->
                                selectedSpeed = newSpd
                                GameStorage.saveLastSpeedName(newSpd.name)
                                SoundEngine.playSfx(SfxType.ROTATE)
                            },
                            onMusicChange = { newMus ->
                                selectedMusic = newMus
                                GameStorage.saveLastMusicName(newMus.name)
                                SoundEngine.playSfx(SfxType.ROTATE)
                                if (!soundMuted && newMus != MusicTrack.OFF) {
                                    SoundEngine.startMusic(newMus)
                                } else {
                                    SoundEngine.stopMusic()
                                }
                            },
                            onToggleMute = {
                                val nextMuted = !soundMuted
                                soundMuted = nextMuted
                                SoundEngine.soundEnabled = !nextMuted
                                GameStorage.saveMuted(nextMuted)
                                if (nextMuted) {
                                    SoundEngine.stopMusic()
                                } else if (selectedMusic != MusicTrack.OFF) {
                                    SoundEngine.startMusic(selectedMusic)
                                }
                            },
                            onToggleVibration = {
                                val nextVibMuted = !vibrationMuted
                                vibrationMuted = nextVibMuted
                                SoundEngine.vibrationEnabled = !nextVibMuted
                                GameStorage.saveVibrationMuted(nextVibMuted)
                                if (!nextVibMuted) {
                                    SoundEngine.triggerHaptic(SfxType.ROTATE)
                                }
                            },
                            onOpenLeaderboard = {
                                SoundEngine.playSfx(SfxType.ROTATE)
                                screenMode = ScreenMode.LEADERBOARD
                            },
                            onContinueGame = {
                                val existing = savedGame
                                if (existing != null) {
                                    selectedLevel = existing.level
                                    selectedSpeed = existing.speed
                                    selectedMusic = existing.music
                                    SoundEngine.soundEnabled = !soundMuted
                                    SoundEngine.vibrationEnabled = !vibrationMuted
                                    SoundEngine.playSfx(SfxType.CLEAR)
                                    resumeFromSaved = true
                                    screenMode = ScreenMode.PLAYING
                                }
                            },
                            onStartNewGame = {
                                GameStorage.clearSavedGame()
                                savedGame = null
                                resumeFromSaved = false
                                SoundEngine.soundEnabled = !soundMuted
                                SoundEngine.vibrationEnabled = !vibrationMuted
                                SoundEngine.playSfx(SfxType.CLEAR)
                                screenMode = ScreenMode.PLAYING
                            }
                        )
                    }
                    ScreenMode.LEADERBOARD -> {
                        RetroLeaderboardScreen(
                            topScore = topScore,
                            currentNick = playerNick,
                            onNickAndScoreUpdated = { updatedNick, syncedScore ->
                                playerNick = updatedNick
                                if (syncedScore > topScore) {
                                    topScore = syncedScore
                                    GameStorage.saveTopScore(syncedScore)
                                }
                            },
                            onBack = {
                                SoundEngine.playSfx(SfxType.MOVE)
                                screenMode = ScreenMode.MENU
                            }
                        )
                    }
                    ScreenMode.PLAYING -> {
                        DrPilulkaGameScreen(
                            initialLevel = selectedLevel,
                            speed = selectedSpeed,
                            music = selectedMusic,
                            soundMuted = soundMuted,
                            vibrationMuted = vibrationMuted,
                            topScore = topScore,
                            initialSavedState = if (resumeFromSaved) savedGame else null,
                            onUpdateTopScore = { newScore ->
                                if (newScore > topScore || !GameStorage.hasRecordedPersonalScore()) {
                                    val best = maxOf(newScore, if (GameStorage.hasRecordedPersonalScore()) topScore else newScore)
                                    topScore = best
                                    GameStorage.saveTopScore(best)
                                    val savedNick = GameStorage.loadPlayerNick()
                                    if (savedNick.length >= 2) {
                                        OnlineLeaderboard.submitScore(savedNick, best) {}
                                    }
                                }
                            },
                            onSaveGameState = { stateToSave ->
                                if (stateToSave == null) {
                                    GameStorage.clearSavedGame()
                                    savedGame = null
                                } else {
                                    val encoded = serializeSavedGame(stateToSave)
                                    GameStorage.saveGameData(encoded)
                                    savedGame = stateToSave
                                }
                            },
                            onToggleMute = {
                                val nextMuted = !soundMuted
                                soundMuted = nextMuted
                                SoundEngine.soundEnabled = !nextMuted
                                GameStorage.saveMuted(nextMuted)
                                if (nextMuted) {
                                    SoundEngine.stopMusic()
                                } else if (selectedMusic != MusicTrack.OFF) {
                                    SoundEngine.startMusic(selectedMusic)
                                }
                            },
                            onToggleVibration = {
                                val nextVibMuted = !vibrationMuted
                                vibrationMuted = nextVibMuted
                                SoundEngine.vibrationEnabled = !nextVibMuted
                                GameStorage.saveVibrationMuted(nextVibMuted)
                                if (!nextVibMuted) {
                                    SoundEngine.triggerHaptic(SfxType.ROTATE)
                                }
                            },
                            onOpenLeaderboard = {
                                SoundEngine.stopMusic()
                                savedGame = deserializeSavedGame(GameStorage.loadGameData())
                                screenMode = ScreenMode.LEADERBOARD
                            },
                            onBackToMenu = {
                                SoundEngine.stopMusic()
                                savedGame = deserializeSavedGame(GameStorage.loadGameData())
                                screenMode = ScreenMode.MENU
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun RetroMenuScreen(
    level: Int,
    speed: SpeedLevel,
    music: MusicTrack,
    soundMuted: Boolean,
    vibrationMuted: Boolean,
    topScore: Int,
    playerNick: String,
    savedGame: SavedGameState?,
    onLevelChange: (Int) -> Unit,
    onSpeedChange: (SpeedLevel) -> Unit,
    onMusicChange: (MusicTrack) -> Unit,
    onToggleMute: () -> Unit,
    onToggleVibration: () -> Unit,
    onOpenLeaderboard: () -> Unit,
    onContinueGame: () -> Unit,
    onStartNewGame: () -> Unit
) {
    var animFrame by remember { mutableIntStateOf(0) }

    LaunchedEffect(Unit) {
        while (true) {
            delay(320L)
            animFrame = (animFrame + 1) % 2
        }
    }

    val virusCount = (level + 1) * 4
    val virusWord = if (virusCount == 4) "VIRY" else "VIRŮ"

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 12.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        // 1. Horni retro lista: Rekord vlevo (klikaci pro otevreni zebricku), prepinace Vibraci a Zvuku vpravo
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .background(Color(0xFF0F172A))
                .border(1.dp, Color(0xFF334155), RoundedCornerShape(8.dp))
                .padding(horizontal = 10.dp, vertical = 7.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .clickable { onOpenLeaderboard() }
                    .padding(vertical = 2.dp, horizontal = 2.dp)
            ) {
                Text(
                    text = "REKORD ${topScore.toString().padStart(7, '0')}",
                    color = Color(0xFFFACC15),
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 13.sp,
                    maxLines = 1,
                    softWrap = false
                )
                Text(
                    text = if (playerNick.isBlank()) "NICK: NENASTAVEN" else "NICK: $playerNick",
                    color = Color(0xFF38BDF8),
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    fontSize = 10.sp,
                    maxLines = 1,
                    softWrap = false
                )
            }
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                MenuToggleChip(
                    label = if (vibrationMuted) "VIB: VYP" else "VIB: ZAP",
                    active = !vibrationMuted,
                    activeColor = Color(0xFF38BDF8),
                    onClick = onToggleVibration
                )
                MenuToggleChip(
                    label = if (soundMuted) "ZVUK: VYP" else "ZVUK: ZAP",
                    active = !soundMuted,
                    activeColor = Color(0xFF4ADE80),
                    onClick = onToggleMute
                )
            }
        }

        // 2. Pixel-artovy banner s doktorem a viry
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(Color(0xFF0F172A))
                .border(3.dp, Color(0xFFE2E8F0), RoundedCornerShape(12.dp))
                .padding(vertical = 8.dp, horizontal = 10.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = "DR. PILULKA",
                    color = Color(0xFFEF4444),
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 26.sp,
                    textAlign = TextAlign.Center,
                    maxLines = 1
                )
                Text(
                    text = "8-BITOVÝ LIKVIDÁTOR VIRŮ",
                    color = Color(0xFF93C5FD),
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    fontSize = 11.sp,
                    textAlign = TextAlign.Center,
                    maxLines = 1
                )
                Spacer(modifier = Modifier.height(6.dp))
                Canvas(modifier = Modifier.size(width = 216.dp, height = 44.dp)) {
                    val cell = size.height * 0.72f
                    val yOff = (size.height - cell) / 2f
                    drawPixelVirus(Offset(size.width * 0.08f, yOff), cell, cell, PillColor.RED, animFrame)
                    drawPixelVirus(Offset(size.width * 0.31f, yOff), cell, cell, PillColor.YELLOW, animFrame)
                    drawPixelVirus(Offset(size.width * 0.54f, yOff), cell, cell, PillColor.BLUE, animFrame)

                    val pillCell = cell * 0.82f
                    val pillY = (size.height - pillCell) / 2f
                    drawCapsuleHalf(
                        topLeft = Offset(size.width * 0.75f, pillY),
                        cellW = pillCell,
                        cellH = pillCell,
                        color = PillColor.RED,
                        kind = CellKind.LEFT
                    )
                    drawCapsuleHalf(
                        topLeft = Offset(size.width * 0.75f + pillCell, pillY),
                        cellW = pillCell,
                        cellH = pillCell,
                        color = PillColor.BLUE,
                        kind = CellKind.RIGHT
                    )
                }
            }
        }

        // 3. Nastaveni hry
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(Color(0xFF0F172A))
                .border(2.dp, Color(0xFF3B82F6), RoundedCornerShape(12.dp))
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // UROVEN VIRU (00 - 20)
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "ÚROVEŇ VIRŮ ($virusCount $virusWord)",
                    color = Color(0xFFFACC15),
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp,
                    textAlign = TextAlign.Center,
                    maxLines = 1
                )
                Spacer(modifier = Modifier.height(5.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally)
                ) {
                    RetroStepButton("-5") { onLevelChange(level - 5) }
                    RetroStepButton("-") { onLevelChange(level - 1) }
                    Box(
                        modifier = Modifier
                            .width(64.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(Color(0xFF070A14))
                            .border(2.dp, Color(0xFFE2E8F0), RoundedCornerShape(6.dp))
                            .padding(vertical = 5.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = level.toString().padStart(2, '0'),
                            color = Color.White,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 19.sp,
                            maxLines = 1
                        )
                    }
                    RetroStepButton("+") { onLevelChange(level + 1) }
                    RetroStepButton("+5") { onLevelChange(level + 5) }
                }
            }

            // RYCHLOST PADU (POMALA / STREDNI / RYCHLA)
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "RYCHLOST PÁDU",
                    color = Color(0xFFFACC15),
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp,
                    textAlign = TextAlign.Center,
                    maxLines = 1
                )
                Spacer(modifier = Modifier.height(5.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    SpeedLevel.entries.forEach { item ->
                        val selected = item == speed
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (selected) Color(0xFFEF4444) else Color(0xFF1E293B))
                                .border(
                                    width = 2.dp,
                                    color = if (selected) Color.White else Color(0xFF475569),
                                    shape = RoundedCornerShape(8.dp)
                                )
                                .clickable { onSpeedChange(item) }
                                .padding(vertical = 8.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = item.label,
                                color = Color.White,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 13.sp,
                                maxLines = 1,
                                softWrap = false
                            )
                        }
                    }
                }
            }

            // DOPROVODNA HUDBA (FEVER / CHILL / VYP)
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "DOPROVODNÁ 8-BIT HUDBA",
                    color = Color(0xFFFACC15),
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp,
                    textAlign = TextAlign.Center,
                    maxLines = 1
                )
                Spacer(modifier = Modifier.height(5.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    MusicTrack.entries.forEach { track ->
                        val selected = track == music
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (selected) Color(0xFF3B82F6) else Color(0xFF1E293B))
                                .border(
                                    width = 2.dp,
                                    color = if (selected) Color.White else Color(0xFF475569),
                                    shape = RoundedCornerShape(8.dp)
                                )
                                .clickable { onMusicChange(track) }
                                .padding(vertical = 8.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = track.label,
                                color = Color.White,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 13.sp,
                                maxLines = 1,
                                softWrap = false
                            )
                        }
                    }
                }
            }
        }

        // 4. Spodni tlacitka (ONLINE REKORDY + POKRACOVAT + NOVA HRA) a copyright paticka
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(Color(0xFF1E293B))
                    .border(2.dp, Color(0xFFFACC15), RoundedCornerShape(10.dp))
                    .clickable { onOpenLeaderboard() }
                    .padding(vertical = 9.dp, horizontal = 10.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "ONLINE REKORDY (TOP 50)",
                    color = Color(0xFFFACC15),
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 14.sp,
                    textAlign = TextAlign.Center,
                    maxLines = 1
                )
            }
            if (savedGame != null) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(Color(0xFF3B82F6))
                        .border(2.dp, Color(0xFFFDE047), RoundedCornerShape(10.dp))
                        .clickable { onContinueGame() }
                        .padding(vertical = 10.dp, horizontal = 8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "POKRAČOVAT (ÚR. ${savedGame.level.toString().padStart(2, '0')} • ${savedGame.score} B.)",
                        color = Color.White,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 13.sp,
                        textAlign = TextAlign.Center,
                        maxLines = 1,
                        softWrap = false
                    )
                }
            }
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(Color(0xFF22C55E))
                    .border(2.dp, Color.White, RoundedCornerShape(10.dp))
                    .clickable { onStartNewGame() }
                    .padding(vertical = 11.dp, horizontal = 12.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "NOVÁ HRA",
                    color = Color(0xFF052E16),
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 18.sp,
                    textAlign = TextAlign.Center,
                    maxLines = 1
                )
            }
            Text(
                text = "© 2026 Created by Pavel Mareš",
                color = Color(0xFF94A3B8),
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                fontSize = 11.sp,
                textAlign = TextAlign.Center,
                maxLines = 1
            )
        }
    }
}

@Composable
private fun RetroLeaderboardScreen(
    topScore: Int,
    currentNick: String,
    onNickAndScoreUpdated: (String, Int) -> Unit,
    onBack: () -> Unit
) {
    var nickInput by remember { mutableStateOf(currentNick) }
    var entries by remember { mutableStateOf<List<LeaderboardEntry>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var isSubmitting by remember { mutableStateOf(false) }
    var statusText by remember { mutableStateOf<String?>(null) }
    var statusIsError by remember { mutableStateOf(false) }

    val personalScoreToSend = if (GameStorage.hasRecordedPersonalScore()) topScore else 0

    fun refreshLeaderboard() {
        isLoading = true
        OnlineLeaderboard.fetchTopScores(limit = 50) { fetched, err ->
            isLoading = false
            entries = fetched
            if (err != null && statusText == null) {
                statusText = err
                statusIsError = true
            }
        }
    }

    LaunchedEffect(Unit) {
        refreshLeaderboard()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 12.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // 1. Horni lista okna rekordu
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .background(Color(0xFF0F172A))
                .border(2.dp, Color(0xFFFACC15), RoundedCornerShape(8.dp))
                .padding(horizontal = 10.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "ONLINE REKORDY (TOP 50)",
                color = Color(0xFFFACC15),
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.ExtraBold,
                fontSize = 14.sp,
                maxLines = 1
            )
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                MiniHeaderButton(
                    label = "OBNOVIT",
                    color = Color(0xFF38BDF8)
                ) {
                    SoundEngine.playSfx(SfxType.ROTATE)
                    statusText = null
                    refreshLeaderboard()
                }
                MiniHeaderButton(
                    label = "ZPĚT",
                    color = Color(0xFF64748B)
                ) {
                    onBack()
                }
            }
        }

        // 2. Panel pro nastaveni / kontrolu prezdivky a odeslani osobniho rekordu
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .background(Color(0xFF0F172A))
                .border(2.dp, Color(0xFF3B82F6), RoundedCornerShape(10.dp))
                .padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "TVOJE PŘEZDÍVKA V DR. PILULCE",
                    color = Color(0xFF93C5FD),
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    fontSize = 11.sp
                )
                Text(
                    text = "OSOBNÍ: $personalScoreToSend B.",
                    color = Color(0xFFFACC15),
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 11.sp
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(0xFF070A14))
                        .border(2.dp, Color(0xFFE2E8F0), RoundedCornerShape(8.dp))
                        .padding(horizontal = 10.dp, vertical = 9.dp),
                    contentAlignment = Alignment.CenterStart
                ) {
                    if (nickInput.isEmpty()) {
                        Text(
                            text = "Zadej nick (2-14 znaků)...",
                            color = Color(0xFF64748B),
                            fontFamily = FontFamily.Monospace,
                            fontSize = 13.sp,
                            maxLines = 1
                        )
                    }
                    BasicTextField(
                        value = nickInput,
                        onValueChange = { next ->
                            if (next.length <= 14) {
                                nickInput = next.replace("\n", "")
                            }
                        },
                        singleLine = true,
                        textStyle = TextStyle(
                            color = Color.White,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 14.sp
                        ),
                        cursorBrush = SolidColor(Color(0xFFFACC15)),
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (isSubmitting) Color(0xFF475569) else Color(0xFF22C55E))
                        .border(2.dp, Color.White, RoundedCornerShape(8.dp))
                        .clickable(enabled = !isSubmitting) {
                            isSubmitting = true
                            statusText = "Ověřuji přezdívku a ukládám..."
                            statusIsError = false
                            OnlineLeaderboard.submitScore(
                                rawNick = nickInput,
                                scoreToSubmit = personalScoreToSend
                            ) { res ->
                                isSubmitting = false
                                statusText = res.message
                                statusIsError = res.status != SubmitResultStatus.SUCCESS
                                if (res.status == SubmitResultStatus.SUCCESS) {
                                    val clean = OnlineLeaderboard.sanitizeNick(nickInput)
                                    nickInput = clean
                                    SoundEngine.playSfx(SfxType.CLEAR)
                                    onNickAndScoreUpdated(clean, res.syncedScore)
                                    refreshLeaderboard()
                                } else {
                                    SoundEngine.playSfx(SfxType.GAME_OVER)
                                }
                            }
                        }
                        .padding(horizontal = 12.dp, vertical = 9.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = if (isSubmitting) "..." else "ULOŽIT",
                        color = Color(0xFF052E16),
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 13.sp,
                        maxLines = 1
                    )
                }
            }

            if (statusText != null) {
                Text(
                    text = statusText ?: "",
                    color = if (statusIsError) Color(0xFFF87171) else Color(0xFF4ADE80),
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    fontSize = 11.sp
                )
            }
        }

        // 3. Posuvna tabulka TOP 50 rekordu
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .background(Color(0xFF0F172A))
                .border(2.dp, Color(0xFF334155), RoundedCornerShape(10.dp))
                .padding(8.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 6.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "#   HRÁČ",
                    color = Color(0xFF94A3B8),
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    fontSize = 11.sp
                )
                Text(
                    text = "BODY",
                    color = Color(0xFF94A3B8),
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    fontSize = 11.sp
                )
            }

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(top = 4.dp),
                contentAlignment = Alignment.Center
            ) {
                when {
                    isLoading -> {
                        Text(
                            text = "Načítám online žebříček...",
                            color = Color(0xFFFACC15),
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp
                        )
                    }
                    entries.isEmpty() -> {
                        Text(
                            text = "Zatím žádné záznamy.\nZadej přezdívku a buď první!",
                            color = Color(0xFF94A3B8),
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp,
                            textAlign = TextAlign.Center
                        )
                    }
                    else -> {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            verticalArrangement = Arrangement.spacedBy(5.dp)
                        ) {
                            items(entries) { item ->
                                val rowBg = when {
                                    item.isCurrentPlayer -> Color(0xFF1E3A8A)
                                    item.rank == 1 -> Color(0xFF422006)
                                    item.rank == 2 -> Color(0xFF1E293B)
                                    item.rank == 3 -> Color(0xFF27171A)
                                    else -> Color(0xFF070A14)
                                }
                                val rowBorder = when {
                                    item.isCurrentPlayer -> Color(0xFF38BDF8)
                                    item.rank == 1 -> Color(0xFFFACC15)
                                    else -> Color(0xFF1E293B)
                                }
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(rowBg)
                                        .border(1.dp, rowBorder, RoundedCornerShape(6.dp))
                                        .padding(horizontal = 8.dp, vertical = 7.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Text(
                                            text = "${item.rank.toString().padStart(2, ' ')}.",
                                            color = if (item.rank <= 3) Color(0xFFFACC15) else Color(0xFF94A3B8),
                                            fontFamily = FontFamily.Monospace,
                                            fontWeight = FontWeight.ExtraBold,
                                            fontSize = 13.sp
                                        )
                                        Text(
                                            text = item.nick,
                                            color = if (item.isCurrentPlayer) Color(0xFF38BDF8) else Color.White,
                                            fontFamily = FontFamily.Monospace,
                                            fontWeight = FontWeight.ExtraBold,
                                            fontSize = 13.sp,
                                            maxLines = 1
                                        )
                                    }
                                    Text(
                                        text = "${item.score.toString().padStart(7, '0')} B.",
                                        color = Color(0xFFFACC15),
                                        fontFamily = FontFamily.Monospace,
                                        fontWeight = FontWeight.ExtraBold,
                                        fontSize = 13.sp
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // 4. Spodni tlacitko Zpet a copyright
        OverlayActionButton("ZPĚT DO MENU", Color(0xFF3B82F6)) {
            onBack()
        }
        Text(
            text = "© 2026 Created by Pavel Mareš",
            color = Color(0xFF94A3B8),
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Bold,
            fontSize = 11.sp,
            textAlign = TextAlign.Center,
            maxLines = 1
        )
    }
}

@Composable
private fun MenuToggleChip(
    label: String,
    active: Boolean,
    activeColor: Color,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(Color(0xFF1E293B))
            .border(
                width = 1.dp,
                color = if (active) activeColor else Color(0xFF475569),
                shape = RoundedCornerShape(6.dp)
            )
            .clickable { onClick() }
            .padding(horizontal = 8.dp, vertical = 5.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            color = if (active) activeColor else Color(0xFFF87171),
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Bold,
            fontSize = 10.sp,
            maxLines = 1,
            softWrap = false
        )
    }
}

@Composable
private fun RetroStepButton(label: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(38.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(Color(0xFF1E293B))
            .border(2.dp, Color(0xFF94A3B8), RoundedCornerShape(8.dp))
            .clickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            color = Color.White,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.ExtraBold,
            fontSize = 14.sp,
            maxLines = 1
        )
    }
}

@Composable
private fun DrPilulkaGameScreen(
    initialLevel: Int,
    speed: SpeedLevel,
    music: MusicTrack,
    soundMuted: Boolean,
    vibrationMuted: Boolean,
    topScore: Int,
    initialSavedState: SavedGameState?,
    onUpdateTopScore: (Int) -> Unit,
    onSaveGameState: (SavedGameState?) -> Unit,
    onToggleMute: () -> Unit,
    onToggleVibration: () -> Unit,
    onOpenLeaderboard: () -> Unit,
    onBackToMenu: () -> Unit
) {
    var currentLevel by remember { mutableIntStateOf(initialSavedState?.level ?: initialLevel) }
    var score by remember { mutableIntStateOf(initialSavedState?.score ?: 0) }
    var pillsThrown by remember { mutableIntStateOf(initialSavedState?.pillsThrown ?: 0) }
    var chainCounter by remember { mutableIntStateOf(0) }
    var phase by remember { mutableStateOf(PlayPhase.FALLING) }

    var board by remember {
        mutableStateOf(initialSavedState?.board ?: generateBottleWithViruses(initialLevel))
    }
    var activeCapsule by remember { mutableStateOf(initialSavedState?.activeCapsule) }
    var nextColorA by remember { mutableStateOf(initialSavedState?.nextColorA ?: randomPillColor()) }
    var nextColorB by remember { mutableStateOf(initialSavedState?.nextColorB ?: randomPillColor()) }

    var docThrowTick by remember { mutableIntStateOf(0) }
    var spriteFrame by remember { mutableIntStateOf(0) }
    val focusRequester = remember { FocusRequester() }

    val redViruses = remember(board) { countViruses(board, PillColor.RED) }
    val yellowViruses = remember(board) { countViruses(board, PillColor.YELLOW) }
    val blueViruses = remember(board) { countViruses(board, PillColor.BLUE) }
    val totalViruses = redViruses + yellowViruses + blueViruses

    fun persistCurrentState(
        targetBoard: Array<Array<GridCell>> = board,
        targetActive: ActiveCapsule? = activeCapsule,
        targetLevel: Int = currentLevel,
        targetScore: Int = score,
        targetPills: Int = pillsThrown,
        targetNextA: PillColor = nextColorA,
        targetNextB: PillColor = nextColorB
    ) {
        if (countAllViruses(targetBoard) <= 0) return
        onSaveGameState(
            SavedGameState(
                level = targetLevel,
                score = targetScore,
                pillsThrown = targetPills,
                speed = speed,
                music = music,
                nextColorA = targetNextA,
                nextColorB = targetNextB,
                activeCapsule = targetActive,
                board = copyGrid(targetBoard)
            )
        )
    }

    fun spawnNextCapsule(currentBoard: Array<Array<GridCell>>) {
        val spawnLeft = currentBoard[0][3].kind != CellKind.EMPTY
        val spawnRight = currentBoard[0][4].kind != CellKind.EMPTY
        if (spawnLeft || spawnRight) {
            activeCapsule = null
            phase = PlayPhase.GAME_OVER
            onSaveGameState(null)
            SoundEngine.stopMusic()
            SoundEngine.playSfx(SfxType.GAME_OVER)
            return
        }
        val spawned = ActiveCapsule(
            x = 3,
            y = 0,
            isVertical = false,
            colorA = nextColorA,
            colorB = nextColorB
        )
        val upcomingA = randomPillColor()
        val upcomingB = randomPillColor()
        val nextPillsCount = pillsThrown + 1

        activeCapsule = spawned
        nextColorA = upcomingA
        nextColorB = upcomingB
        pillsThrown = nextPillsCount
        chainCounter = 0
        docThrowTick = 2
        phase = PlayPhase.FALLING

        persistCurrentState(
            targetBoard = currentBoard,
            targetActive = spawned,
            targetPills = nextPillsCount,
            targetNextA = upcomingA,
            targetNextB = upcomingB
        )
    }

    fun startStage(lvl: Int) {
        val freshBoard = generateBottleWithViruses(lvl)
        board = freshBoard
        pillsThrown = 0
        chainCounter = 0
        nextColorA = randomPillColor()
        nextColorB = randomPillColor()
        spawnNextCapsule(freshBoard)
        if (!soundMuted && music != MusicTrack.OFF) {
            SoundEngine.startMusic(music)
        }
    }

    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
        if (initialSavedState != null && countAllViruses(initialSavedState.board) > 0) {
            if (initialSavedState.activeCapsule == null) {
                spawnNextCapsule(initialSavedState.board)
            } else {
                phase = PlayPhase.FALLING
            }
            if (!soundMuted && music != MusicTrack.OFF) {
                SoundEngine.startMusic(music)
            }
        } else {
            startStage(currentLevel)
        }
    }

    LaunchedEffect(Unit) {
        while (true) {
            delay(280L)
            spriteFrame = (spriteFrame + 1) % 2
            if (docThrowTick > 0) docThrowTick -= 1
        }
    }

    fun canPlace(
        grid: Array<Array<GridCell>>,
        x: Int,
        y: Int,
        isVert: Boolean
    ): Boolean {
        if (x !in 0 until GRID_COLS || y !in 0 until GRID_ROWS) return false
        if (grid[y][x].kind != CellKind.EMPTY) return false
        val x2 = if (isVert) x else x + 1
        val y2 = if (isVert) y - 1 else y
        if (x2 !in 0 until GRID_COLS) return false
        if (y2 >= 0) {
            if (y2 >= GRID_ROWS || grid[y2][x2].kind != CellKind.EMPTY) return false
        }
        return true
    }

    fun moveHorizontal(dx: Int) {
        if (phase != PlayPhase.FALLING) return
        val cap = activeCapsule ?: return
        if (canPlace(board, cap.x + dx, cap.y, cap.isVertical)) {
            val updated = cap.copy(x = cap.x + dx)
            activeCapsule = updated
            SoundEngine.playSfx(SfxType.MOVE)
            persistCurrentState(targetActive = updated)
        }
    }

    fun rotateCapsule(clockwise: Boolean) {
        if (phase != PlayPhase.FALLING) return
        val cap = activeCapsule ?: return
        if (!cap.isVertical) {
            val newColorA = if (clockwise) cap.colorB else cap.colorA
            val newColorB = if (clockwise) cap.colorA else cap.colorB
            if (canPlace(board, cap.x, cap.y, isVert = true)) {
                val updated = cap.copy(
                    isVertical = true,
                    colorA = newColorA,
                    colorB = newColorB
                )
                activeCapsule = updated
                SoundEngine.playSfx(SfxType.ROTATE)
                persistCurrentState(targetActive = updated)
            } else if (cap.y + 1 < GRID_ROWS && canPlace(board, cap.x, cap.y + 1, isVert = true)) {
                val updated = cap.copy(
                    y = cap.y + 1,
                    isVertical = true,
                    colorA = newColorA,
                    colorB = newColorB
                )
                activeCapsule = updated
                SoundEngine.playSfx(SfxType.ROTATE)
                persistCurrentState(targetActive = updated)
            }
        } else {
            val newColorA = if (clockwise) cap.colorA else cap.colorB
            val newColorB = if (clockwise) cap.colorB else cap.colorA
            if (canPlace(board, cap.x, cap.y, isVert = false)) {
                val updated = cap.copy(
                    isVertical = false,
                    colorA = newColorA,
                    colorB = newColorB
                )
                activeCapsule = updated
                SoundEngine.playSfx(SfxType.ROTATE)
                persistCurrentState(targetActive = updated)
            } else if (canPlace(board, cap.x - 1, cap.y, isVert = false)) {
                val updated = cap.copy(
                    x = cap.x - 1,
                    isVertical = false,
                    colorA = newColorA,
                    colorB = newColorB
                )
                activeCapsule = updated
                SoundEngine.playSfx(SfxType.ROTATE)
                persistCurrentState(targetActive = updated)
            }
        }
    }

    fun lockAndEvaluate(cap: ActiveCapsule) {
        val nextGrid = copyGrid(board)
        val x1 = cap.x
        val y1 = cap.y
        val x2 = if (cap.isVertical) cap.x else cap.x + 1
        val y2 = if (cap.isVertical) cap.y - 1 else cap.y

        if (cap.isVertical) {
            if (y2 >= 0) {
                nextGrid[y1][x1] = GridCell(CellKind.BOTTOM, cap.colorA)
                nextGrid[y2][x2] = GridCell(CellKind.TOP, cap.colorB)
            } else {
                nextGrid[y1][x1] = GridCell(CellKind.SINGLE, cap.colorA)
            }
        } else {
            nextGrid[y1][x1] = GridCell(CellKind.LEFT, cap.colorA)
            nextGrid[y2][x2] = GridCell(CellKind.RIGHT, cap.colorB)
        }

        activeCapsule = null
        SoundEngine.playSfx(SfxType.DROP)

        val matchResult = markMatchesForClearing(nextGrid, speed, chainCounter)
        board = matchResult.grid
        if (matchResult.matchedAny) {
            chainCounter += 1
            val newScore = score + matchResult.pointsEarned
            score = newScore
            onUpdateTopScore(newScore)
            when {
                chainCounter >= 2 -> SoundEngine.playSfx(SfxType.COMBO)
                matchResult.virusesKilled > 0 -> SoundEngine.playSfx(SfxType.VIRUS_KILL)
                else -> SoundEngine.playSfx(SfxType.CLEAR)
            }
            phase = PlayPhase.CLEARING_ANIM
        } else {
            spawnNextCapsule(nextGrid)
        }
    }

    fun stepDown(fromUserPress: Boolean = false): Boolean {
        if (phase != PlayPhase.FALLING) return false
        val cap = activeCapsule ?: return false
        return if (canPlace(board, cap.x, cap.y + 1, cap.isVertical)) {
            val updated = cap.copy(y = cap.y + 1)
            activeCapsule = updated
            if (fromUserPress) {
                SoundEngine.triggerHaptic(SfxType.MOVE)
            }
            persistCurrentState(targetActive = updated)
            true
        } else {
            lockAndEvaluate(cap)
            false
        }
    }

    fun hardDrop() {
        if (phase != PlayPhase.FALLING) return
        var cap = activeCapsule ?: return
        while (canPlace(board, cap.x, cap.y + 1, cap.isVertical)) {
            cap = cap.copy(y = cap.y + 1)
        }
        activeCapsule = cap
        lockAndEvaluate(cap)
    }

    LaunchedEffect(phase, speed) {
        when (phase) {
            PlayPhase.FALLING -> {
                while (phase == PlayPhase.FALLING) {
                    val speedup = (pillsThrown / 10) * 15L
                    val delayMs = (speed.baseDelayMs - speedup).coerceAtLeast(280L)
                    delay(delayMs)
                    if (phase == PlayPhase.FALLING && activeCapsule != null) {
                        stepDown(fromUserPress = false)
                    }
                }
            }
            PlayPhase.CLEARING_ANIM -> {
                delay(240L)
                val cleaned = removeClearedCells(board)
                board = cleaned
                if (countAllViruses(cleaned) == 0) {
                    phase = PlayPhase.STAGE_CLEAR
                    SoundEngine.stopMusic()
                    SoundEngine.playSfx(SfxType.STAGE_CLEAR)
                } else {
                    phase = PlayPhase.GRAVITY_STEP
                }
            }
            PlayPhase.GRAVITY_STEP -> {
                while (phase == PlayPhase.GRAVITY_STEP) {
                    delay(120L)
                    val gravStep = applySingleGravityStep(board)
                    board = gravStep.grid
                    if (!gravStep.movedAny) {
                        val matchResult = markMatchesForClearing(gravStep.grid, speed, chainCounter)
                        board = matchResult.grid
                        if (matchResult.matchedAny) {
                            chainCounter += 1
                            val newScore = score + matchResult.pointsEarned
                            score = newScore
                            onUpdateTopScore(newScore)
                            if (chainCounter >= 2) {
                                SoundEngine.playSfx(SfxType.COMBO)
                            } else if (matchResult.virusesKilled > 0) {
                                SoundEngine.playSfx(SfxType.VIRUS_KILL)
                            } else {
                                SoundEngine.playSfx(SfxType.CLEAR)
                            }
                            phase = PlayPhase.CLEARING_ANIM
                        } else {
                            spawnNextCapsule(gravStep.grid)
                        }
                    }
                }
            }
            else -> Unit
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .focusRequester(focusRequester)
            .focusable()
            .onKeyEvent { event ->
                if (event.type == KeyEventType.KeyDown) {
                    when (event.key) {
                        Key.DirectionLeft -> {
                            moveHorizontal(-1)
                            true
                        }
                        Key.DirectionRight -> {
                            moveHorizontal(1)
                            true
                        }
                        Key.DirectionDown -> {
                            stepDown(fromUserPress = true)
                            true
                        }
                        Key.DirectionUp -> {
                            rotateCapsule(clockwise = true)
                            true
                        }
                        Key.Z -> {
                            rotateCapsule(clockwise = false)
                            true
                        }
                        Key.X, Key.Spacebar -> {
                            rotateCapsule(clockwise = true)
                            true
                        }
                        else -> false
                    }
                } else {
                    false
                }
            }
            .padding(horizontal = 6.dp, vertical = 6.dp),
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        // 1. HORNI LISTA: Rekord, Skore + ovladani pauzy/vibraci/zvuku/menu
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .background(Color(0xFF0F172A))
                .border(2.dp, Color(0xFF334155), RoundedCornerShape(8.dp))
                .padding(horizontal = 8.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "REKORD ${topScore.toString().padStart(7, '0')}",
                    color = Color(0xFF94A3B8),
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    fontSize = 10.sp,
                    maxLines = 1
                )
                Text(
                    text = "SKÓRE  ${score.toString().padStart(7, '0')}",
                    color = Color(0xFFFACC15),
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 12.sp,
                    maxLines = 1
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                MiniHeaderButton(
                    label = if (phase == PlayPhase.PAUSED) "HRA" else "PAUZA",
                    color = Color(0xFF3B82F6)
                ) {
                    if (phase == PlayPhase.FALLING) {
                        phase = PlayPhase.PAUSED
                        SoundEngine.stopMusic()
                    } else if (phase == PlayPhase.PAUSED) {
                        phase = PlayPhase.FALLING
                        if (!soundMuted && music != MusicTrack.OFF) {
                            SoundEngine.startMusic(music)
                        }
                    }
                }
                MiniHeaderButton(
                    label = if (vibrationMuted) "VIB-" else "VIB",
                    color = if (vibrationMuted) Color(0xFFEF4444) else Color(0xFF38BDF8)
                ) {
                    onToggleVibration()
                }
                MiniHeaderButton(
                    label = if (soundMuted) "NĚMÝ" else "ZVUK",
                    color = if (soundMuted) Color(0xFFEF4444) else Color(0xFF22C55E)
                ) {
                    onToggleMute()
                }
                MiniHeaderButton(
                    label = "MENU",
                    color = Color(0xFF64748B)
                ) {
                    onBackToMenu()
                }
            }
        }

        // 2. HLAVNI CAST: Bocni panely (0.19f) a lahev uprostred (0.62f)
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(vertical = 4.dp),
            contentAlignment = Alignment.Center
        ) {
            Row(
                modifier = Modifier.fillMaxSize(),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // LEVY KOMPAKTNI PANEL (19 % sirky): Lupa se 3 tancicimi viry + pocitadlo viru
                Column(
                    modifier = Modifier
                        .weight(0.19f)
                        .fillMaxHeight(),
                    verticalArrangement = Arrangement.SpaceBetween,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(0.95f)
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color(0xFF0F172A))
                            .border(2.dp, Color(0xFF3B82F6), RoundedCornerShape(8.dp))
                            .padding(3.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Canvas(modifier = Modifier.fillMaxSize()) {
                            val radius = size.minDimension * 0.46f
                            val center = Offset(size.width / 2f, size.height / 2f)
                            drawCircle(
                                color = Color(0xFF050811),
                                radius = radius,
                                center = center
                            )
                            drawCircle(
                                color = Color(0xFF93C5FD),
                                radius = radius,
                                center = center,
                                style = Stroke(width = 3f)
                            )
                            val vSize = radius * 0.62f
                            if (redViruses > 0) {
                                drawPixelVirus(
                                    topLeft = Offset(center.x - vSize / 2f, center.y - radius * 0.68f),
                                    cellW = vSize,
                                    cellH = vSize,
                                    color = PillColor.RED,
                                    frame = spriteFrame
                                )
                            }
                            if (yellowViruses > 0) {
                                drawPixelVirus(
                                    topLeft = Offset(center.x - radius * 0.68f, center.y + radius * 0.02f),
                                    cellW = vSize,
                                    cellH = vSize,
                                    color = PillColor.YELLOW,
                                    frame = (spriteFrame + 1) % 2
                                )
                            }
                            if (blueViruses > 0) {
                                drawPixelVirus(
                                    topLeft = Offset(center.x + radius * 0.08f, center.y + radius * 0.02f),
                                    cellW = vSize,
                                    cellH = vSize,
                                    color = PillColor.BLUE,
                                    frame = spriteFrame
                                )
                            }
                        }
                    }

                    // Tabulka zbyvajicich viru
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color(0xFF0F172A))
                            .border(2.dp, Color(0xFF475569), RoundedCornerShape(8.dp))
                            .padding(horizontal = 4.dp, vertical = 6.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(3.dp)
                    ) {
                        Text(
                            text = "VIRY",
                            color = Color(0xFFFACC15),
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            fontSize = 10.sp
                        )
                        Text(
                            text = totalViruses.toString().padStart(2, '0'),
                            color = Color.White,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 18.sp
                        )
                        VirusColorCounterRow("Č", PillColor.RED.mainHex, redViruses)
                        VirusColorCounterRow("Ž", PillColor.YELLOW.mainHex, yellowViruses)
                        VirusColorCounterRow("M", PillColor.BLUE.mainHex, blueViruses)
                    }
                }

                // STREDNI SIROKY PANEL (62 % sirky): Lahev 8x16
                Box(
                    modifier = Modifier
                        .weight(0.62f)
                        .fillMaxHeight(),
                    contentAlignment = Alignment.Center
                ) {
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        val neckUnits = 1.25f
                        val maxCellW = size.width / (GRID_COLS + 0.35f)
                        val maxCellH = size.height / (GRID_ROWS + neckUnits + 0.35f)
                        val cellH = minOf(maxCellH, maxCellW * 1.05f)
                        val cellW = minOf(maxCellW, cellH * 1.22f)

                        val gridWidth = cellW * GRID_COLS
                        val gridHeight = cellH * GRID_ROWS
                        val gridLeft = (size.width - gridWidth) / 2f
                        val totalBottleH = gridHeight + cellH * neckUnits
                        val bottleTopOffset = (size.height - totalBottleH) / 2f
                        val gridTop = bottleTopOffset + cellH * neckUnits

                        val neckLeft = gridLeft + cellW * 2.5f
                        val neckRight = gridLeft + cellW * 5.5f
                        val rimTop = bottleTopOffset + cellH * 0.15f
                        val neckTop = bottleTopOffset + cellH * 0.50f

                        drawRoundRect(
                            color = Color(0xFF05070E),
                            topLeft = Offset(gridLeft - 4f, gridTop - 4f),
                            size = Size(gridWidth + 8f, gridHeight + 8f),
                            cornerRadius = CornerRadius(8f, 8f)
                        )

                        for (r in 0..GRID_ROWS) {
                            val y = gridTop + r * cellH
                            drawLine(
                                color = Color(0xFF111827),
                                start = Offset(gridLeft, y),
                                end = Offset(gridLeft + gridWidth, y),
                                strokeWidth = 1f
                            )
                        }
                        for (c in 0..GRID_COLS) {
                            val x = gridLeft + c * cellW
                            drawLine(
                                color = Color(0xFF111827),
                                start = Offset(x, gridTop),
                                end = Offset(x, gridTop + gridHeight),
                                strokeWidth = 1f
                            )
                        }

                        val bottleWallColor = Color(0xFFE2E8F0)
                        val wallStroke = 5f
                        drawRect(
                            color = bottleWallColor,
                            topLeft = Offset(neckLeft - cellW * 0.25f, rimTop),
                            size = Size((neckRight - neckLeft) + cellW * 0.5f, neckTop - rimTop)
                        )
                        drawLine(
                            color = bottleWallColor,
                            start = Offset(neckLeft, neckTop),
                            end = Offset(neckLeft, gridTop),
                            strokeWidth = wallStroke
                        )
                        drawLine(
                            color = bottleWallColor,
                            start = Offset(neckRight, neckTop),
                            end = Offset(neckRight, gridTop),
                            strokeWidth = wallStroke
                        )
                        drawLine(
                            color = bottleWallColor,
                            start = Offset(gridLeft - 3f, gridTop),
                            end = Offset(neckLeft, gridTop),
                            strokeWidth = wallStroke
                        )
                        drawLine(
                            color = bottleWallColor,
                            start = Offset(neckRight, gridTop),
                            end = Offset(gridLeft + gridWidth + 3f, gridTop),
                            strokeWidth = wallStroke
                        )
                        drawLine(
                            color = bottleWallColor,
                            start = Offset(gridLeft - 3f, gridTop),
                            end = Offset(gridLeft - 3f, gridTop + gridHeight + 3f),
                            strokeWidth = wallStroke
                        )
                        drawLine(
                            color = bottleWallColor,
                            start = Offset(gridLeft + gridWidth + 3f, gridTop),
                            end = Offset(gridLeft + gridWidth + 3f, gridTop + gridHeight + 3f),
                            strokeWidth = wallStroke
                        )
                        drawLine(
                            color = bottleWallColor,
                            start = Offset(gridLeft - 3f, gridTop + gridHeight + 3f),
                            end = Offset(gridLeft + gridWidth + 3f, gridTop + gridHeight + 3f),
                            strokeWidth = wallStroke
                        )

                        for (r in 0 until GRID_ROWS) {
                            for (c in 0 until GRID_COLS) {
                                val cellItem = board[r][c]
                                val cColor = cellItem.color ?: continue
                                val topLeft = Offset(gridLeft + c * cellW, gridTop + r * cellH)
                                when (cellItem.kind) {
                                    CellKind.EMPTY -> Unit
                                    CellKind.VIRUS -> drawPixelVirus(topLeft, cellW, cellH, cColor, spriteFrame)
                                    CellKind.CLEARING -> drawClearingPop(topLeft, cellW, cellH, cColor)
                                    else -> drawCapsuleHalf(topLeft, cellW, cellH, cColor, cellItem.kind)
                                }
                            }
                        }

                        activeCapsule?.let { cap ->
                            val pos1 = Offset(gridLeft + cap.x * cellW, gridTop + cap.y * cellH)
                            if (cap.isVertical) {
                                drawCapsuleHalf(pos1, cellW, cellH, cap.colorA, CellKind.BOTTOM)
                                if (cap.y - 1 >= 0) {
                                    val pos2 = Offset(gridLeft + cap.x * cellW, gridTop + (cap.y - 1) * cellH)
                                    drawCapsuleHalf(pos2, cellW, cellH, cap.colorB, CellKind.TOP)
                                }
                            } else {
                                drawCapsuleHalf(pos1, cellW, cellH, cap.colorA, CellKind.LEFT)
                                val pos2 = Offset(gridLeft + (cap.x + 1) * cellW, gridTop + cap.y * cellH)
                                drawCapsuleHalf(pos2, cellW, cellH, cap.colorB, CellKind.RIGHT)
                            }
                        }
                    }
                }

                // PRAVY KOMPAKTNI PANEL (19 % sirky): Doktor s dalsi kapsli (DALSI) + statistiky
                Column(
                    modifier = Modifier
                        .weight(0.19f)
                        .fillMaxHeight(),
                    verticalArrangement = Arrangement.SpaceBetween,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color(0xFF0F172A))
                            .border(2.dp, Color(0xFFE2E8F0), RoundedCornerShape(8.dp))
                            .padding(horizontal = 4.dp, vertical = 6.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "DALŠÍ",
                            color = Color(0xFFFACC15),
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            fontSize = 10.sp
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Canvas(modifier = Modifier.size(width = 40.dp, height = 20.dp)) {
                            val half = size.height * 0.9f
                            val startX = (size.width - half * 2f) / 2f
                            drawCapsuleHalf(Offset(startX, 0f), half, half, nextColorA, CellKind.LEFT)
                            drawCapsuleHalf(Offset(startX + half, 0f), half, half, nextColorB, CellKind.RIGHT)
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        Canvas(modifier = Modifier.size(44.dp)) {
                            drawPixelDoctor(throwing = docThrowTick > 0)
                        }
                    }

                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color(0xFF0F172A))
                            .border(2.dp, Color(0xFF475569), RoundedCornerShape(8.dp))
                            .padding(horizontal = 4.dp, vertical = 6.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        StatBadge("ÚROVEŇ", currentLevel.toString().padStart(2, '0'), Color(0xFF38BDF8))
                        StatBadge("RYCHLOST", speed.shortLabel, Color(0xFFF43F5E))
                        StatBadge("HUDBA", music.label, Color(0xFFA855F7))
                    }
                }
            }

            // Prekryvne okno pri VYCISTENI LAHVE, KONCI HRY nebo PAUZE
            if (phase == PlayPhase.STAGE_CLEAR || phase == PlayPhase.GAME_OVER || phase == PlayPhase.PAUSED) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color(0xCC050811)),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        modifier = Modifier
                            .width(260.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color(0xFF0F172A))
                            .border(3.dp, Color.White, RoundedCornerShape(12.dp))
                            .padding(18.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        when (phase) {
                            PlayPhase.STAGE_CLEAR -> {
                                Text(
                                    text = "LÁHEV VYČIŠTĚNA!",
                                    color = Color(0xFF4ADE80),
                                    fontFamily = FontFamily.Monospace,
                                    fontWeight = FontWeight.ExtraBold,
                                    fontSize = 20.sp,
                                    textAlign = TextAlign.Center
                                )
                                Text(
                                    text = "Všechny viry v lahvi byly zlikvidovány!",
                                    color = Color(0xFFE2E8F0),
                                    fontFamily = FontFamily.Monospace,
                                    textAlign = TextAlign.Center,
                                    fontSize = 13.sp
                                )
                                OverlayActionButton("DALŠÍ ÚROVEŇ (${(currentLevel + 1).coerceAtMost(20)})", Color(0xFF22C55E)) {
                                    val nextLvl = (currentLevel + 1).coerceAtMost(20)
                                    currentLevel = nextLvl
                                    GameStorage.saveLastLevel(nextLvl)
                                    startStage(nextLvl)
                                }
                            }
                            PlayPhase.GAME_OVER -> {
                                Text(
                                    text = "KONEC HRY",
                                    color = Color(0xFFEF4444),
                                    fontFamily = FontFamily.Monospace,
                                    fontWeight = FontWeight.ExtraBold,
                                    fontSize = 22.sp,
                                    textAlign = TextAlign.Center
                                )
                                Text(
                                    text = "Hrdlo lahve se ucpalo!\nSkóre: $score b. • Zbylo virů: $totalViruses",
                                    color = Color(0xFFE2E8F0),
                                    fontFamily = FontFamily.Monospace,
                                    textAlign = TextAlign.Center,
                                    fontSize = 12.sp
                                )
                                OverlayActionButton("ZKUSIT ZNOVU", Color(0xFF3B82F6)) {
                                    score = 0
                                    startStage(currentLevel)
                                }
                                OverlayActionButton("ONLINE REKORDY", Color(0xFFB45309)) {
                                    onOpenLeaderboard()
                                }
                            }
                            PlayPhase.PAUSED -> {
                                Text(
                                    text = "PAUZA",
                                    color = Color(0xFFFACC15),
                                    fontFamily = FontFamily.Monospace,
                                    fontWeight = FontWeight.ExtraBold,
                                    fontSize = 22.sp,
                                    textAlign = TextAlign.Center
                                )
                                OverlayActionButton("POKRAČOVAT VE HŘE", Color(0xFF22C55E)) {
                                    phase = PlayPhase.FALLING
                                    if (!soundMuted && music != MusicTrack.OFF) {
                                        SoundEngine.startMusic(music)
                                    }
                                }
                            }
                            else -> Unit
                        }
                        OverlayActionButton("ZPĚT DO MENU", Color(0xFF475569)) {
                            onBackToMenu()
                        }
                    }
                }
            }
        }

        // 3. SPODNI RETRO OVLADACI PANEL (NES D-Pad vlevo + kulata tlacitka se smerem otaceni vpravo)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(Color(0xFF0F172A))
                .border(2.dp, Color(0xFF334155), RoundedCornerShape(12.dp))
                .padding(horizontal = 10.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                DPadButton("◄", widthDp = 46) { moveHorizontal(-1) }
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    DPadButton("▼", widthDp = 46) { stepDown(fromUserPress = true) }
                    DPadButton("⏬", widthDp = 46) { hardDrop() }
                }
                DPadButton("►", widthDp = 46) { moveHorizontal(1) }
            }

            Row(
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                ArcadeRoundButton("↺", Color(0xFFEF4444)) {
                    rotateCapsule(clockwise = false)
                }
                ArcadeRoundButton("↻", Color(0xFFEF4444)) {
                    rotateCapsule(clockwise = true)
                }
            }
        }
    }
}

@Composable
private fun MiniHeaderButton(label: String, color: Color, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(color.copy(alpha = 0.2f))
            .border(1.dp, color, RoundedCornerShape(6.dp))
            .clickable { onClick() }
            .padding(horizontal = 6.dp, vertical = 5.dp)
    ) {
        Text(
            text = label,
            color = Color.White,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Bold,
            fontSize = 10.sp,
            maxLines = 1,
            softWrap = false
        )
    }
}

@Composable
private fun VirusColorCounterRow(label: String, color: Color, count: Int) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
            Box(
                modifier = Modifier
                    .size(7.dp)
                    .clip(CircleShape)
                    .background(color)
            )
            Text(
                text = label,
                color = Color(0xFFCBD5E1),
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                fontSize = 10.sp
            )
        }
        Text(
            text = count.toString().padStart(2, '0'),
            color = Color.White,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Bold,
            fontSize = 10.sp
        )
    }
}

@Composable
private fun StatBadge(title: String, value: String, accent: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = title,
            color = Color(0xFF94A3B8),
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Bold,
            fontSize = 9.sp,
            maxLines = 1
        )
        Text(
            text = value,
            color = accent,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.ExtraBold,
            fontSize = 13.sp,
            maxLines = 1
        )
    }
}

@Composable
private fun OverlayActionButton(label: String, bgColor: Color, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(bgColor)
            .border(2.dp, Color.White, RoundedCornerShape(8.dp))
            .clickable { onClick() }
            .padding(vertical = 10.dp, horizontal = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            color = Color.White,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.ExtraBold,
            fontSize = 13.sp,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun DPadButton(symbol: String, widthDp: Int, onAction: () -> Unit) {
    Box(
        modifier = Modifier
            .width(widthDp.dp)
            .height(42.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(Color(0xFF1E293B))
            .border(2.dp, Color(0xFF94A3B8), RoundedCornerShape(8.dp))
            .pointerInput(Unit) {
                detectTapGestures(onPress = {
                    onAction()
                })
            },
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = symbol,
            color = Color.White,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.ExtraBold,
            fontSize = 17.sp
        )
    }
}

@Composable
private fun ArcadeRoundButton(symbol: String, color: Color, onAction: () -> Unit) {
    Box(
        modifier = Modifier
            .requiredSize(56.dp)
            .clip(CircleShape)
            .background(color)
            .border(3.dp, Color(0xFFFDE047), CircleShape)
            .pointerInput(Unit) {
                detectTapGestures(onPress = {
                    onAction()
                })
            },
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = symbol,
            color = Color.White,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.ExtraBold,
            fontSize = 26.sp
        )
    }
}

// ============================================================================
// PIXEL-ART VYKRESLOVANI (VIRY, SPOJENE KAPSLE, DOKTOR, EFEKTY)
// ============================================================================

private fun DrawScope.drawPixelVirus(
    topLeft: Offset,
    cellW: Float,
    cellH: Float,
    color: PillColor,
    frame: Int
) {
    val spriteFrame0 = arrayOf(
        intArrayOf(0, 1, 0, 0, 0, 0, 1, 0),
        intArrayOf(0, 0, 1, 1, 1, 1, 0, 0),
        intArrayOf(0, 1, 2, 3, 1, 2, 3, 0),
        intArrayOf(1, 1, 2, 2, 1, 2, 2, 1),
        intArrayOf(1, 1, 1, 1, 1, 1, 1, 1),
        intArrayOf(0, 1, 3, 2, 3, 2, 1, 0),
        intArrayOf(0, 1, 0, 1, 1, 0, 1, 0),
        intArrayOf(1, 0, 0, 0, 0, 0, 0, 1)
    )
    val spriteFrame1 = arrayOf(
        intArrayOf(1, 0, 0, 0, 0, 0, 0, 1),
        intArrayOf(0, 1, 1, 1, 1, 1, 1, 0),
        intArrayOf(0, 1, 3, 2, 1, 3, 2, 0),
        intArrayOf(1, 1, 2, 2, 1, 2, 2, 1),
        intArrayOf(1, 1, 1, 1, 1, 1, 1, 1),
        intArrayOf(0, 1, 3, 3, 3, 3, 1, 0),
        intArrayOf(0, 0, 1, 0, 0, 1, 0, 0),
        intArrayOf(0, 1, 1, 0, 0, 1, 1, 0)
    )
    val matrix = if (frame % 2 == 0) spriteFrame0 else spriteFrame1
    val pxW = cellW / 8f
    val pxH = cellH / 8f

    for (r in 0 until 8) {
        for (c in 0 until 8) {
            val code = matrix[r][c]
            if (code == 0) continue
            val pColor = when (code) {
                1 -> color.mainHex
                2 -> Color.White
                else -> Color(0xFF070A14)
            }
            drawRect(
                color = pColor,
                topLeft = Offset(topLeft.x + c * pxW, topLeft.y + r * pxH),
                size = Size(pxW + 0.5f, pxH + 0.5f)
            )
        }
    }
}

private fun DrawScope.drawCapsuleHalf(
    topLeft: Offset,
    cellW: Float,
    cellH: Float,
    color: PillColor,
    kind: CellKind
) {
    val padX = cellW * 0.06f
    val padY = cellH * 0.06f
    val x = topLeft.x + padX
    val y = topLeft.y + padY
    val w = cellW - padX * 2f
    val h = cellH - padY * 2f
    val r = minOf(w, h) * 0.46f
    val border = 1.6f

    drawRoundRect(
        color = color.darkHex,
        topLeft = Offset(x, y),
        size = Size(w, h),
        cornerRadius = CornerRadius(r, r)
    )
    when (kind) {
        CellKind.LEFT -> {
            drawRect(
                color = color.darkHex,
                topLeft = Offset(x + w * 0.5f, y),
                size = Size((topLeft.x + cellW) - (x + w * 0.5f), h)
            )
        }
        CellKind.RIGHT -> {
            drawRect(
                color = color.darkHex,
                topLeft = Offset(topLeft.x, y),
                size = Size((x + w * 0.5f) - topLeft.x, h)
            )
        }
        CellKind.TOP -> {
            drawRect(
                color = color.darkHex,
                topLeft = Offset(x, y + h * 0.5f),
                size = Size(w, (topLeft.y + cellH) - (y + h * 0.5f))
            )
        }
        CellKind.BOTTOM -> {
            drawRect(
                color = color.darkHex,
                topLeft = Offset(x, topLeft.y),
                size = Size(w, (y + h * 0.5f) - topLeft.y)
            )
        }
        else -> Unit
    }

    drawRoundRect(
        color = color.mainHex,
        topLeft = Offset(x + border, y + border),
        size = Size(w - border * 2f, h - border * 2f),
        cornerRadius = CornerRadius(r, r)
    )
    when (kind) {
        CellKind.LEFT -> {
            drawRect(
                color = color.mainHex,
                topLeft = Offset(x + w * 0.5f, y + border),
                size = Size((topLeft.x + cellW - 1f) - (x + w * 0.5f), h - border * 2f)
            )
        }
        CellKind.RIGHT -> {
            drawRect(
                color = color.mainHex,
                topLeft = Offset(topLeft.x + 1f, y + border),
                size = Size((x + w * 0.5f) - (topLeft.x + 1f), h - border * 2f)
            )
        }
        CellKind.TOP -> {
            drawRect(
                color = color.mainHex,
                topLeft = Offset(x + border, y + h * 0.5f),
                size = Size(w - border * 2f, (topLeft.y + cellH - 1f) - (y + h * 0.5f))
            )
        }
        CellKind.BOTTOM -> {
            drawRect(
                color = color.mainHex,
                topLeft = Offset(x + border, topLeft.y + 1f),
                size = Size(w - border * 2f, (y + h * 0.5f) - (topLeft.y + 1f))
            )
        }
        else -> Unit
    }

    drawRoundRect(
        color = color.lightHex,
        topLeft = Offset(x + w * 0.20f, y + h * 0.18f),
        size = Size(w * 0.28f, h * 0.20f),
        cornerRadius = CornerRadius(3f, 3f)
    )
}

private fun DrawScope.drawClearingPop(
    topLeft: Offset,
    cellW: Float,
    cellH: Float,
    color: PillColor
) {
    val center = Offset(topLeft.x + cellW / 2f, topLeft.y + cellH / 2f)
    val base = minOf(cellW, cellH)
    drawCircle(
        color = Color.White,
        radius = base * 0.42f,
        center = center,
        style = Stroke(width = 3f)
    )
    drawCircle(
        color = color.lightHex,
        radius = base * 0.22f,
        center = center
    )
}

private fun DrawScope.drawPixelDoctor(throwing: Boolean) {
    val px = size.minDimension / 8f
    drawRect(Color(0xFF38BDF8), Offset(px * 3f, px * 0.5f), Size(px * 2f, px * 1.2f))
    drawRect(Color(0xFF78350F), Offset(px * 2f, px * 1.2f), Size(px * 4f, px * 1.2f))
    drawRect(Color(0xFFFDE68A), Offset(px * 2f, px * 2.2f), Size(px * 4f, px * 2.2f))
    drawRect(Color(0xFF0F172A), Offset(px * 2.6f, px * 2.6f), Size(px * 0.7f, px * 0.7f))
    drawRect(Color(0xFF0F172A), Offset(px * 4.6f, px * 2.6f), Size(px * 0.7f, px * 0.7f))
    drawRect(Color(0xFF78350F), Offset(px * 2.5f, px * 3.6f), Size(px * 3f, px * 0.6f))
    drawRect(Color.White, Offset(px * 1.5f, px * 4.4f), Size(px * 5f, px * 3.0f))
    drawRect(Color(0xFFEF4444), Offset(px * 3.6f, px * 4.6f), Size(px * 0.8f, px * 2.0f))
    val armY = if (throwing) px * 2.0f else px * 4.4f
    drawRect(Color.White, Offset(px * 0.4f, armY), Size(px * 1.2f, px * 1.8f))
}

// ============================================================================
// SERIALIZACE A NACITANI ROZEHRANE HRY
// ============================================================================

private fun serializeSavedGame(state: SavedGameState): String {
    val capStr = state.activeCapsule?.let {
        "${it.x},${it.y},${if (it.isVertical) 1 else 0},${it.colorA.name},${it.colorB.name}"
    } ?: "NONE"

    val cellsSb = StringBuilder()
    for (r in 0 until GRID_ROWS) {
        for (c in 0 until GRID_COLS) {
            if (r > 0 || c > 0) cellsSb.append(';')
            val cell = state.board[r][c]
            val safeKind = if (cell.kind == CellKind.CLEARING) CellKind.EMPTY else cell.kind
            val colorIdx = if (safeKind == CellKind.EMPTY) -1 else (cell.color?.ordinal ?: -1)
            cellsSb.append(safeKind.ordinal).append(':').append(colorIdx)
        }
    }

    return listOf(
        "V1",
        state.level.toString(),
        state.score.toString(),
        state.pillsThrown.toString(),
        state.speed.name,
        state.music.name,
        state.nextColorA.name,
        state.nextColorB.name,
        capStr,
        cellsSb.toString()
    ).joinToString("|")
}

private fun deserializeSavedGame(raw: String?): SavedGameState? {
    if (raw.isNullOrBlank()) return null
    return try {
        val parts = raw.split("|")
        if (parts.size < 10 || parts[0] != "V1") return null
        val level = parts[1].toInt().coerceIn(0, 20)
        val score = parts[2].toInt().coerceAtLeast(0)
        val pillsThrown = parts[3].toInt().coerceAtLeast(0)
        val speed = SpeedLevel.entries.find { it.name == parts[4] } ?: SpeedLevel.MED
        val music = MusicTrack.entries.find { it.name == parts[5] } ?: MusicTrack.FEVER
        val nextA = PillColor.entries.find { it.name == parts[6] } ?: PillColor.RED
        val nextB = PillColor.entries.find { it.name == parts[7] } ?: PillColor.BLUE

        val activeCap = if (parts[8] == "NONE") {
            null
        } else {
            val cp = parts[8].split(",")
            ActiveCapsule(
                x = cp[0].toInt().coerceIn(0, GRID_COLS - 1),
                y = cp[1].toInt().coerceIn(0, GRID_ROWS - 1),
                isVertical = cp[2] == "1",
                colorA = PillColor.entries.find { it.name == cp[3] } ?: PillColor.RED,
                colorB = PillColor.entries.find { it.name == cp[4] } ?: PillColor.BLUE
            )
        }

        val cellTokens = parts[9].split(";")
        if (cellTokens.size != GRID_ROWS * GRID_COLS) return null
        val board = Array(GRID_ROWS) { Array(GRID_COLS) { GridCell() } }
        var idx = 0
        for (r in 0 until GRID_ROWS) {
            for (c in 0 until GRID_COLS) {
                val kv = cellTokens[idx++].split(":")
                val kindOrd = kv[0].toIntOrNull() ?: 0
                val colOrd = kv.getOrNull(1)?.toIntOrNull() ?: -1
                val kind = CellKind.entries.getOrNull(kindOrd) ?: CellKind.EMPTY
                val color = if (colOrd >= 0) PillColor.entries.getOrNull(colOrd) else null
                board[r][c] = if (kind == CellKind.EMPTY || color == null) {
                    GridCell()
                } else {
                    GridCell(kind = kind, color = color)
                }
            }
        }

        SavedGameState(
            level = level,
            score = score,
            pillsThrown = pillsThrown,
            speed = speed,
            music = music,
            nextColorA = nextA,
            nextColorB = nextB,
            activeCapsule = activeCap,
            board = board
        )
    } catch (_: Throwable) {
        null
    }
}

// ============================================================================
// CISTA HERNI LOGIKA (GENEROVANI VIRU, MATCH-4, ROZPAD KAPSLI A GRAVITACE)
// ============================================================================

private fun randomPillColor(): PillColor {
    val all = PillColor.entries
    return all[Random.nextInt(all.size)]
}

private fun copyGrid(source: Array<Array<GridCell>>): Array<Array<GridCell>> {
    return Array(GRID_ROWS) { r ->
        Array(GRID_COLS) { c ->
            source[r][c]
        }
    }
}

private fun generateBottleWithViruses(level: Int): Array<Array<GridCell>> {
    val grid = Array(GRID_ROWS) { Array(GRID_COLS) { GridCell() } }
    val virusTarget = ((level.coerceIn(0, 20) + 1) * 4).coerceAtMost(84)
    val minRow = when {
        level >= 17 -> 4
        level >= 13 -> 5
        else -> 6
    }

    val colors = PillColor.entries
    var placed = 0
    var safety = 0

    while (placed < virusTarget && safety < 4000) {
        safety += 1
        val r = Random.nextInt(minRow, GRID_ROWS)
        val c = Random.nextInt(0, GRID_COLS)
        if (grid[r][c].kind != CellKind.EMPTY) continue

        val chosenColor = colors[placed % colors.size]
        val horizSame = (c >= 2 && grid[r][c - 1].color == chosenColor && grid[r][c - 2].color == chosenColor) ||
            (c <= GRID_COLS - 3 && grid[r][c + 1].color == chosenColor && grid[r][c + 2].color == chosenColor) ||
            (c in 1..(GRID_COLS - 2) && grid[r][c - 1].color == chosenColor && grid[r][c + 1].color == chosenColor)
        val vertSame = (r >= 2 && grid[r - 1][c].color == chosenColor && grid[r - 2][c].color == chosenColor) ||
            (r <= GRID_ROWS - 3 && grid[r + 1][c].color == chosenColor && grid[r + 2][c].color == chosenColor) ||
            (r in 1..(GRID_ROWS - 2) && grid[r - 1][c].color == chosenColor && grid[r + 1][c].color == chosenColor)

        if (horizSame || vertSame) continue
        grid[r][c] = GridCell(kind = CellKind.VIRUS, color = chosenColor)
        placed += 1
    }
    return grid
}

private data class MatchEvaluation(
    val grid: Array<Array<GridCell>>,
    val matchedAny: Boolean,
    val virusesKilled: Int,
    val pointsEarned: Int
)

private fun markMatchesForClearing(
    source: Array<Array<GridCell>>,
    speed: SpeedLevel,
    chainIndex: Int
): MatchEvaluation {
    val toClear = Array(GRID_ROWS) { BooleanArray(GRID_COLS) }
    var foundAny = false

    for (r in 0 until GRID_ROWS) {
        var c = 0
        while (c < GRID_COLS) {
            val col = source[r][c].color
            if (col == null || source[r][c].kind == CellKind.EMPTY || source[r][c].kind == CellKind.CLEARING) {
                c += 1
                continue
            }
            var end = c + 1
            while (end < GRID_COLS &&
                source[r][end].color == col &&
                source[r][end].kind != CellKind.EMPTY &&
                source[r][end].kind != CellKind.CLEARING
            ) {
                end += 1
            }
            val len = end - c
            if (len >= 4) {
                foundAny = true
                for (k in c until end) {
                    toClear[r][k] = true
                }
            }
            c = end
        }
    }

    for (c in 0 until GRID_COLS) {
        var r = 0
        while (r < GRID_ROWS) {
            val col = source[r][c].color
            if (col == null || source[r][c].kind == CellKind.EMPTY || source[r][c].kind == CellKind.CLEARING) {
                r += 1
                continue
            }
            var end = r + 1
            while (end < GRID_ROWS &&
                source[end][c].color == col &&
                source[end][c].kind != CellKind.EMPTY &&
                source[end][c].kind != CellKind.CLEARING
            ) {
                end += 1
            }
            val len = end - r
            if (len >= 4) {
                foundAny = true
                for (k in r until end) {
                    toClear[k][c] = true
                }
            }
            r = end
        }
    }

    if (!foundAny) {
        return MatchEvaluation(source, matchedAny = false, virusesKilled = 0, pointsEarned = 0)
    }

    val next = copyGrid(source)
    var virusesKilled = 0
    var tilesCleared = 0

    for (r in 0 until GRID_ROWS) {
        for (c in 0 until GRID_COLS) {
            if (!toClear[r][c]) continue
            tilesCleared += 1
            val cell = next[r][c]
            if (cell.kind == CellKind.VIRUS) {
                virusesKilled += 1
            }
            when (cell.kind) {
                CellKind.LEFT -> {
                    if (c + 1 < GRID_COLS && !toClear[r][c + 1] && next[r][c + 1].kind == CellKind.RIGHT) {
                        next[r][c + 1] = next[r][c + 1].copy(kind = CellKind.SINGLE)
                    }
                }
                CellKind.RIGHT -> {
                    if (c - 1 >= 0 && !toClear[r][c - 1] && next[r][c - 1].kind == CellKind.LEFT) {
                        next[r][c - 1] = next[r][c - 1].copy(kind = CellKind.SINGLE)
                    }
                }
                CellKind.TOP -> {
                    if (r + 1 < GRID_ROWS && !toClear[r + 1][c] && next[r + 1][c].kind == CellKind.BOTTOM) {
                        next[r + 1][c] = next[r + 1][c].copy(kind = CellKind.SINGLE)
                    }
                }
                CellKind.BOTTOM -> {
                    if (r - 1 >= 0 && !toClear[r - 1][c] && next[r - 1][c].kind == CellKind.TOP) {
                        next[r - 1][c] = next[r - 1][c].copy(kind = CellKind.SINGLE)
                    }
                }
                else -> Unit
            }
            next[r][c] = GridCell(kind = CellKind.CLEARING, color = cell.color)
        }
    }

    val speedMult = when (speed) {
        SpeedLevel.LOW -> 1
        SpeedLevel.MED -> 2
        SpeedLevel.HI -> 3
    }
    val chainMult = (chainIndex + 1).coerceAtMost(8)
    val basePoints = (virusesKilled * 200) + (tilesCleared * 25)
    val totalPoints = basePoints * speedMult * chainMult

    return MatchEvaluation(
        grid = next,
        matchedAny = true,
        virusesKilled = virusesKilled,
        pointsEarned = totalPoints
    )
}

private fun removeClearedCells(source: Array<Array<GridCell>>): Array<Array<GridCell>> {
    val next = copyGrid(source)
    for (r in 0 until GRID_ROWS) {
        for (c in 0 until GRID_COLS) {
            if (next[r][c].kind == CellKind.CLEARING) {
                next[r][c] = GridCell(kind = CellKind.EMPTY, color = null)
            }
        }
    }
    return next
}

private data class GravityStepResult(
    val grid: Array<Array<GridCell>>,
    val movedAny: Boolean
)

private fun applySingleGravityStep(source: Array<Array<GridCell>>): GravityStepResult {
    val next = copyGrid(source)
    var moved = false

    for (r in (GRID_ROWS - 2) downTo 0) {
        var c = 0
        while (c < GRID_COLS) {
            val cell = next[r][c]
            when (cell.kind) {
                CellKind.SINGLE -> {
                    if (next[r + 1][c].kind == CellKind.EMPTY) {
                        next[r + 1][c] = cell
                        next[r][c] = GridCell()
                        moved = true
                    }
                    c += 1
                }
                CellKind.BOTTOM -> {
                    if (next[r + 1][c].kind == CellKind.EMPTY) {
                        next[r + 1][c] = cell
                        if (r - 1 >= 0 && next[r - 1][c].kind == CellKind.TOP) {
                            next[r][c] = next[r - 1][c]
                            next[r - 1][c] = GridCell()
                        } else {
                            next[r][c] = GridCell()
                        }
                        moved = true
                    }
                    c += 1
                }
                CellKind.LEFT -> {
                    if (c + 1 < GRID_COLS && next[r][c + 1].kind == CellKind.RIGHT) {
                        if (next[r + 1][c].kind == CellKind.EMPTY && next[r + 1][c + 1].kind == CellKind.EMPTY) {
                            next[r + 1][c] = cell
                            next[r + 1][c + 1] = next[r][c + 1]
                            next[r][c] = GridCell()
                            next[r][c + 1] = GridCell()
                            moved = true
                        }
                        c += 2
                    } else {
                        c += 1
                    }
                }
                else -> {
                    c += 1
                }
            }
        }
    }
    return GravityStepResult(next, moved)
}

private fun countViruses(grid: Array<Array<GridCell>>, color: PillColor): Int {
    var count = 0
    for (r in 0 until GRID_ROWS) {
        for (c in 0 until GRID_COLS) {
            val cell = grid[r][c]
            if (cell.kind == CellKind.VIRUS && cell.color == color) {
                count += 1
            }
        }
    }
    return count
}

private fun countAllViruses(grid: Array<Array<GridCell>>): Int {
    var count = 0
    for (r in 0 until GRID_ROWS) {
        for (c in 0 until GRID_COLS) {
            if (grid[r][c].kind == CellKind.VIRUS) {
                count += 1
            }
        }
    }
    return count
}
