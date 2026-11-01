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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
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

enum class SpeedLevel(val label: String, val baseDelayMs: Long) {
    LOW("LOW", 1050L),
    MED("MED", 750L),
    HI("HI", 480L)
}

enum class ScreenMode {
    MENU,
    PLAYING
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

@Composable
fun App() {
    MaterialTheme {
        var screenMode by remember { mutableStateOf(ScreenMode.MENU) }
        var selectedLevel by remember { mutableIntStateOf(0) }
        var selectedSpeed by remember { mutableStateOf(SpeedLevel.MED) }
        var selectedMusic by remember { mutableStateOf(MusicTrack.FEVER) }
        var soundMuted by remember { mutableStateOf(false) }
        var topScore by remember { mutableIntStateOf(10000) }

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
                    .windowInsetsPadding(WindowInsets.systemBars)
            ) {
                when (screenMode) {
                    ScreenMode.MENU -> {
                        RetroMenuScreen(
                            level = selectedLevel,
                            speed = selectedSpeed,
                            music = selectedMusic,
                            soundMuted = soundMuted,
                            topScore = topScore,
                            onLevelChange = { newLvl ->
                                selectedLevel = newLvl.coerceIn(0, 20)
                                SoundEngine.playSfx(SfxType.MOVE)
                            },
                            onSpeedChange = { newSpd ->
                                selectedSpeed = newSpd
                                SoundEngine.playSfx(SfxType.ROTATE)
                            },
                            onMusicChange = { newMus ->
                                selectedMusic = newMus
                                SoundEngine.playSfx(SfxType.ROTATE)
                                if (!soundMuted && newMus != MusicTrack.OFF) {
                                    SoundEngine.startMusic(newMus)
                                } else {
                                    SoundEngine.stopMusic()
                                }
                            },
                            onToggleMute = {
                                soundMuted = !soundMuted
                                SoundEngine.soundEnabled = !soundMuted
                                if (soundMuted) {
                                    SoundEngine.stopMusic()
                                } else if (selectedMusic != MusicTrack.OFF) {
                                    SoundEngine.startMusic(selectedMusic)
                                }
                            },
                            onStartGame = {
                                SoundEngine.soundEnabled = !soundMuted
                                SoundEngine.playSfx(SfxType.CLEAR)
                                screenMode = ScreenMode.PLAYING
                            }
                        )
                    }

                    ScreenMode.PLAYING -> {
                        DrPilulkaGameScreen(
                            initialLevel = selectedLevel,
                            speed = selectedSpeed,
                            music = selectedMusic,
                            soundMuted = soundMuted,
                            topScore = topScore,
                            onUpdateTopScore = { if (it > topScore) topScore = it },
                            onToggleMute = {
                                soundMuted = !soundMuted
                                SoundEngine.soundEnabled = !soundMuted
                                if (soundMuted) {
                                    SoundEngine.stopMusic()
                                } else if (selectedMusic != MusicTrack.OFF) {
                                    SoundEngine.startMusic(selectedMusic)
                                }
                            },
                            onBackToMenu = {
                                SoundEngine.stopMusic()
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
    topScore: Int,
    onLevelChange: (Int) -> Unit,
    onSpeedChange: (SpeedLevel) -> Unit,
    onMusicChange: (MusicTrack) -> Unit,
    onToggleMute: () -> Unit,
    onStartGame: () -> Unit
) {
    var animFrame by remember { mutableIntStateOf(0) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(320L)
            animFrame = (animFrame + 1) % 2
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 14.dp, vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        // Horni retro lista
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "TOP ${topScore.toString().padStart(7, '0')}",
                color = Color(0xFFFACC15),
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                fontSize = 15.sp
            )
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .background(Color(0xFF1E293B))
                    .border(1.dp, Color(0xFF475569), RoundedCornerShape(6.dp))
                    .clickable { onToggleMute() }
                    .padding(horizontal = 10.dp, vertical = 6.dp)
            ) {
                Text(
                    text = if (soundMuted) "ZVUK: VYP" else "ZVUK: ZAP",
                    color = if (soundMuted) Color(0xFFF87171) else Color(0xFF4ADE80),
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp
                )
            }
        }

        // Pixel-artovy banner s doktorem a viry
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(Color(0xFF0F172A))
                .border(3.dp, Color(0xFFE2E8F0), RoundedCornerShape(12.dp))
                .padding(vertical = 12.dp, horizontal = 10.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = "DR. PILULKA",
                    color = Color(0xFFEF4444),
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 28.sp,
                    textAlign = TextAlign.Center
                )
                Text(
                    text = "8-BIT RETRO VIRUS BUSTER",
                    color = Color(0xFF93C5FD),
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(10.dp))
                Canvas(modifier = Modifier.size(width = 220.dp, height = 52.dp)) {
                    val cell = size.height * 0.72f
                    val yOff = (size.height - cell) / 2f
                    drawPixelVirus(Offset(size.width * 0.08f, yOff), cell, PillColor.RED, animFrame)
                    drawPixelVirus(Offset(size.width * 0.31f, yOff), cell, PillColor.YELLOW, animFrame)
                    drawPixelVirus(Offset(size.width * 0.54f, yOff), cell, PillColor.BLUE, animFrame)
                    // Spojena dvoubarevna kapsle vpravo
                    val pillCell = cell * 0.82f
                    val pillY = (size.height - pillCell) / 2f
                    drawCapsuleHalf(
                        topLeft = Offset(size.width * 0.75f, pillY),
                        cellSize = pillCell,
                        color = PillColor.RED,
                        kind = CellKind.LEFT
                    )
                    drawCapsuleHalf(
                        topLeft = Offset(size.width * 0.75f + pillCell, pillY),
                        cellSize = pillCell,
                        color = PillColor.BLUE,
                        kind = CellKind.RIGHT
                    )
                }
            }
        }

        // Nastaveni hry po vzoru NES Dr. Mario
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(Color(0xFF0F172A))
                .border(2.dp, Color(0xFF3B82F6), RoundedCornerShape(12.dp))
                .padding(horizontal = 12.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // 1. VIRUS LEVEL (00 - 20) - vsech 5 prvku zarovnanych uvnitr radku
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "VIRUS LEVEL (${(level + 1) * 4} VIRU)",
                    color = Color(0xFFFACC15),
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally)
                ) {
                    RetroStepButton("-5") { onLevelChange(level - 5) }
                    RetroStepButton("-") { onLevelChange(level - 1) }
                    Box(
                        modifier = Modifier
                            .width(66.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(Color(0xFF070A14))
                            .border(2.dp, Color(0xFFE2E8F0), RoundedCornerShape(6.dp))
                            .padding(vertical = 7.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = level.toString().padStart(2, '0'),
                            color = Color.White,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 20.sp
                        )
                    }
                    RetroStepButton("+") { onLevelChange(level + 1) }
                    RetroStepButton("+5") { onLevelChange(level + 5) }
                }
            }

            // 2. SPEED (LOW / MED / HI)
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "SPEED (RYCHLOST PADU)",
                    color = Color(0xFFFACC15),
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
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
                                .padding(vertical = 10.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = item.label,
                                color = Color.White,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 15.sp
                            )
                        }
                    }
                }
            }

            // 3. MUSIC TYPE (FEVER / CHILL / OFF)
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "MUSIC TYPE (8-BIT HUDBA)",
                    color = Color(0xFFFACC15),
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
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
                                .padding(vertical = 10.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = track.label,
                                color = Color.White,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 15.sp
                            )
                        }
                    }
                }
            }
        }

        // Tlacitko START (vycentrovane, na jeden radek)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .background(Color(0xFF22C55E))
                .border(3.dp, Color.White, RoundedCornerShape(10.dp))
                .clickable { onStartGame() }
                .padding(vertical = 14.dp, horizontal = 12.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "START HRY",
                color = Color(0xFF052E16),
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.ExtraBold,
                fontSize = 20.sp,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
private fun RetroStepButton(label: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(42.dp)
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
            fontSize = 15.sp
        )
    }
}

@Composable
private fun DrPilulkaGameScreen(
    initialLevel: Int,
    speed: SpeedLevel,
    music: MusicTrack,
    soundMuted: Boolean,
    topScore: Int,
    onUpdateTopScore: (Int) -> Unit,
    onToggleMute: () -> Unit,
    onBackToMenu: () -> Unit
) {
    var currentLevel by remember { mutableIntStateOf(initialLevel) }
    var score by remember { mutableIntStateOf(0) }
    var pillsThrown by remember { mutableIntStateOf(0) }
    var chainCounter by remember { mutableIntStateOf(0) }
    var phase by remember { mutableStateOf(PlayPhase.FALLING) }

    var board by remember { mutableStateOf(generateBottleWithViruses(initialLevel)) }
    var activeCapsule by remember { mutableStateOf<ActiveCapsule?>(null) }
    var nextColorA by remember { mutableStateOf(randomPillColor()) }
    var nextColorB by remember { mutableStateOf(randomPillColor()) }
    var docThrowTick by remember { mutableIntStateOf(0) }
    var spriteFrame by remember { mutableIntStateOf(0) }

    val focusRequester = remember { FocusRequester() }

    // Pocet zbyvajicich viru celkem i podle barev (pro lupu vlevo)
    val redViruses = remember(board) { countViruses(board, PillColor.RED) }
    val yellowViruses = remember(board) { countViruses(board, PillColor.YELLOW) }
    val blueViruses = remember(board) { countViruses(board, PillColor.BLUE) }
    val totalViruses = redViruses + yellowViruses + blueViruses

    fun spawnNextCapsule(currentBoard: Array<Array<GridCell>>) {
        val spawnLeft = currentBoard[0][3].kind != CellKind.EMPTY
        val spawnRight = currentBoard[0][4].kind != CellKind.EMPTY
        if (spawnLeft || spawnRight) {
            activeCapsule = null
            phase = PlayPhase.GAME_OVER
            SoundEngine.stopMusic()
            SoundEngine.playSfx(SfxType.GAME_OVER)
            return
        }
        activeCapsule = ActiveCapsule(
            x = 3,
            y = 0,
            isVertical = false,
            colorA = nextColorA,
            colorB = nextColorB
        )
        nextColorA = randomPillColor()
        nextColorB = randomPillColor()
        pillsThrown += 1
        chainCounter = 0
        docThrowTick = 2
        phase = PlayPhase.FALLING
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

    // Spusteni prvniho levelu
    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
        startStage(currentLevel)
    }

    // Animace tanecniho kroku viru v lahvi a pod lupou
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
            activeCapsule = cap.copy(x = cap.x + dx)
            SoundEngine.playSfx(SfxType.MOVE)
        }
    }

    fun rotateCapsule(clockwise: Boolean) {
        if (phase != PlayPhase.FALLING) return
        val cap = activeCapsule ?: return

        if (!cap.isVertical) {
            val newColorA = if (clockwise) cap.colorB else cap.colorA
            val newColorB = if (clockwise) cap.colorA else cap.colorB
            if (canPlace(board, cap.x, cap.y, isVert = true)) {
                activeCapsule = cap.copy(
                    isVertical = true,
                    colorA = newColorA,
                    colorB = newColorB
                )
                SoundEngine.playSfx(SfxType.ROTATE)
            } else if (cap.y + 1 < GRID_ROWS && canPlace(board, cap.x, cap.y + 1, isVert = true)) {
                activeCapsule = cap.copy(
                    y = cap.y + 1,
                    isVertical = true,
                    colorA = newColorA,
                    colorB = newColorB
                )
                SoundEngine.playSfx(SfxType.ROTATE)
            }
        } else {
            val newColorA = if (clockwise) cap.colorA else cap.colorB
            val newColorB = if (clockwise) cap.colorB else cap.colorA
            if (canPlace(board, cap.x, cap.y, isVert = false)) {
                activeCapsule = cap.copy(
                    isVertical = false,
                    colorA = newColorA,
                    colorB = newColorB
                )
                SoundEngine.playSfx(SfxType.ROTATE)
            } else if (canPlace(board, cap.x - 1, cap.y, isVert = false)) {
                activeCapsule = cap.copy(
                    x = cap.x - 1,
                    isVertical = false,
                    colorA = newColorA,
                    colorB = newColorB
                )
                SoundEngine.playSfx(SfxType.ROTATE)
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
            score += matchResult.pointsEarned
            onUpdateTopScore(score)
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

    fun stepDown(): Boolean {
        if (phase != PlayPhase.FALLING) return false
        val cap = activeCapsule ?: return false
        return if (canPlace(board, cap.x, cap.y + 1, cap.isVertical)) {
            activeCapsule = cap.copy(y = cap.y + 1)
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

    // Hlavni herní smycka (pohodovy pad pilulky, animace zmizeni a kompletni kaskadova gravitace)
    LaunchedEffect(phase, speed) {
        when (phase) {
            PlayPhase.FALLING -> {
                while (phase == PlayPhase.FALLING) {
                    val speedup = (pillsThrown / 10) * 15L
                    val delayMs = (speed.baseDelayMs - speedup).coerceAtLeast(280L)
                    delay(delayMs)
                    if (phase == PlayPhase.FALLING && activeCapsule != null) {
                        stepDown()
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
                        // Vsechny uvolnene pulky pilulek dopadly -> kontrola kaskadoveho komba
                        val matchResult = markMatchesForClearing(gravStep.grid, speed, chainCounter)
                        board = matchResult.grid
                        if (matchResult.matchedAny) {
                            chainCounter += 1
                            score += matchResult.pointsEarned
                            onUpdateTopScore(score)
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
                            stepDown()
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
            .padding(horizontal = 8.dp, vertical = 6.dp),
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        // 1. HORNÍ LIŠTA: Skóre + ovládání zvuku/pauzy/menu
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .background(Color(0xFF0F172A))
                .border(2.dp, Color(0xFF334155), RoundedCornerShape(8.dp))
                .padding(horizontal = 10.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "TOP ${topScore.toString().padStart(7, '0')}",
                    color = Color(0xFF94A3B8),
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    fontSize = 11.sp,
                    maxLines = 1
                )
                Text(
                    text = "SCORE ${score.toString().padStart(7, '0')}",
                    color = Color(0xFFFACC15),
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 13.sp,
                    maxLines = 1
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
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
                    label = if (soundMuted) "MUTE" else "ZVUK",
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

        // 2. HLAVNÍ STŘEDNÍ ČÁST: Vlevo Lupa s viry, uprostřed Láhev 8x16, vpravo Doktor + statistiky
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(vertical = 4.dp),
            contentAlignment = Alignment.Center
        ) {
            Row(
                modifier = Modifier.fillMaxSize(),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // LEVÝ PANEL: Lupa se 3 tančícími viry + počítadlo virů podle barev
                Column(
                    modifier = Modifier
                        .weight(0.27f)
                        .fillMaxHeight(),
                    verticalArrangement = Arrangement.SpaceBetween,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // Lupa s tančícími viry
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(0.85f)
                            .clip(RoundedCornerShape(10.dp))
                            .background(Color(0xFF0F172A))
                            .border(2.dp, Color(0xFF3B82F6), RoundedCornerShape(10.dp))
                            .padding(4.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Canvas(modifier = Modifier.fillMaxSize()) {
                            val radius = size.minDimension * 0.45f
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
                                style = Stroke(width = 4f)
                            )

                            val vSize = radius * 0.62f
                            if (redViruses > 0) {
                                drawPixelVirus(
                                    topLeft = Offset(center.x - vSize / 2f, center.y - radius * 0.68f),
                                    cellSize = vSize,
                                    color = PillColor.RED,
                                    frame = spriteFrame
                                )
                            }
                            if (yellowViruses > 0) {
                                drawPixelVirus(
                                    topLeft = Offset(center.x - radius * 0.68f, center.y + radius * 0.02f),
                                    cellSize = vSize,
                                    color = PillColor.YELLOW,
                                    frame = (spriteFrame + 1) % 2
                                )
                            }
                            if (blueViruses > 0) {
                                drawPixelVirus(
                                    topLeft = Offset(center.x + radius * 0.08f, center.y + radius * 0.02f),
                                    cellSize = vSize,
                                    color = PillColor.BLUE,
                                    frame = spriteFrame
                                )
                            }
                        }
                    }

                    // Tabulka zbývajících virů
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color(0xFF0F172A))
                            .border(2.dp, Color(0xFF475569), RoundedCornerShape(8.dp))
                            .padding(6.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = "VIRUS",
                            color = Color(0xFFFACC15),
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            fontSize = 11.sp
                        )
                        Text(
                            text = totalViruses.toString().padStart(2, '0'),
                            color = Color.White,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 20.sp
                        )
                        VirusColorCounterRow("R", PillColor.RED.mainHex, redViruses)
                        VirusColorCounterRow("Y", PillColor.YELLOW.mainHex, yellowViruses)
                        VirusColorCounterRow("B", PillColor.BLUE.mainHex, blueViruses)
                    }
                }

                // STŘEDNÍ PANEL: Lékařská láhev 8 × 16 s hrdlem
                Box(
                    modifier = Modifier
                        .weight(0.46f)
                        .fillMaxHeight(),
                    contentAlignment = Alignment.Center
                ) {
                    Canvas(
                        modifier = Modifier
                            .fillMaxHeight()
                            .aspectRatio(8f / 17.6f)
                    ) {
                        val neckUnits = 1.4f
                        val cellW = size.width / (GRID_COLS + 0.6f)
                        val cellH = size.height / (GRID_ROWS + neckUnits + 0.4f)
                        val cell = minOf(cellW, cellH)

                        val gridWidth = cell * GRID_COLS
                        val gridHeight = cell * GRID_ROWS
                        val gridLeft = (size.width - gridWidth) / 2f
                        val gridTop = cell * neckUnits

                        // Hrdlo lahve nahoře (otevřené nad sloupci 3 a 4)
                        val neckLeft = gridLeft + cell * 2.5f
                        val neckRight = gridLeft + cell * 5.5f
                        val rimTop = cell * 0.2f
                        val neckTop = cell * 0.55f

                        // Vnitřní tmavé pozadí lahve
                        drawRoundRect(
                            color = Color(0xFF05070E),
                            topLeft = Offset(gridLeft - 4f, gridTop - 4f),
                            size = Size(gridWidth + 8f, gridHeight + 8f),
                            cornerRadius = CornerRadius(8f, 8f)
                        )

                        // Jemná pixelová mřížka uvnitř lahve
                        for (r in 0..GRID_ROWS) {
                            val y = gridTop + r * cell
                            drawLine(
                                color = Color(0xFF111827),
                                start = Offset(gridLeft, y),
                                end = Offset(gridLeft + gridWidth, y),
                                strokeWidth = 1f
                            )
                        }
                        for (c in 0..GRID_COLS) {
                            val x = gridLeft + c * cell
                            drawLine(
                                color = Color(0xFF111827),
                                start = Offset(x, gridTop),
                                end = Offset(x, gridTop + gridHeight),
                                strokeWidth = 1f
                            )
                        }

                        // Stěny hrdla a prstenec lahve
                        val bottleWallColor = Color(0xFFE2E8F0)
                        val wallStroke = 5f
                        // Horní okraj hrdla
                        drawRect(
                            color = bottleWallColor,
                            topLeft = Offset(neckLeft - cell * 0.25f, rimTop),
                            size = Size((neckRight - neckLeft) + cell * 0.5f, neckTop - rimTop)
                        )
                        // Svislé stěny hrdla
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
                        // Ramena lahve
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
                        // Boční a spodní stěna lahve
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

                        // Vykreslení obsahu mřížky 8 × 16
                        for (r in 0 until GRID_ROWS) {
                            for (c in 0 until GRID_COLS) {
                                val cellItem = board[r][c]
                                val cColor = cellItem.color ?: continue
                                val topLeft = Offset(gridLeft + c * cell, gridTop + r * cell)
                                when (cellItem.kind) {
                                    CellKind.EMPTY -> Unit
                                    CellKind.VIRUS -> drawPixelVirus(topLeft, cell, cColor, spriteFrame)
                                    CellKind.CLEARING -> drawClearingPop(topLeft, cell, cColor)
                                    else -> drawCapsuleHalf(topLeft, cell, cColor, cellItem.kind)
                                }
                            }
                        }

                        // Vykreslení právě padající kapsle
                        activeCapsule?.let { cap ->
                            val pos1 = Offset(gridLeft + cap.x * cell, gridTop + cap.y * cell)
                            if (cap.isVertical) {
                                drawCapsuleHalf(pos1, cell, cap.colorA, CellKind.BOTTOM)
                                if (cap.y - 1 >= 0) {
                                    val pos2 = Offset(gridLeft + cap.x * cell, gridTop + (cap.y - 1) * cell)
                                    drawCapsuleHalf(pos2, cell, cap.colorB, CellKind.TOP)
                                }
                            } else {
                                drawCapsuleHalf(pos1, cell, cap.colorA, CellKind.LEFT)
                                val pos2 = Offset(gridLeft + (cap.x + 1) * cell, gridTop + cap.y * cell)
                                drawCapsuleHalf(pos2, cell, cap.colorB, CellKind.RIGHT)
                            }
                        }
                    }
                }

                // PRAVÝ PANEL: Doktor s další kapslí (NEXT) + LEVEL + SPEED
                Column(
                    modifier = Modifier
                        .weight(0.27f)
                        .fillMaxHeight(),
                    verticalArrangement = Arrangement.SpaceBetween,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // Box s doktorem a NEXT kapslí
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(Color(0xFF0F172A))
                            .border(2.dp, Color(0xFFE2E8F0), RoundedCornerShape(10.dp))
                            .padding(6.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "NEXT",
                            color = Color(0xFFFACC15),
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            fontSize = 11.sp
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        // Náhled další pilulky
                        Canvas(modifier = Modifier.size(width = 48.dp, height = 24.dp)) {
                            val half = size.height * 0.9f
                            val startX = (size.width - half * 2f) / 2f
                            drawCapsuleHalf(Offset(startX, 0f), half, nextColorA, CellKind.LEFT)
                            drawCapsuleHalf(Offset(startX + half, 0f), half, nextColorB, CellKind.RIGHT)
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        // Pixel-art postavička Dr. Pilulky v bílém plášti
                        Canvas(modifier = Modifier.size(56.dp)) {
                            drawPixelDoctor(throwing = docThrowTick > 0)
                        }
                    }

                    // Informační tabulka LEVEL / SPEED / HUDBA
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color(0xFF0F172A))
                            .border(2.dp, Color(0xFF475569), RoundedCornerShape(8.dp))
                            .padding(6.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        StatBadge("LEVEL", currentLevel.toString().padStart(2, '0'), Color(0xFF38BDF8))
                        StatBadge("SPEED", speed.label, Color(0xFFF43F5E))
                        StatBadge("HUDBA", music.label, Color(0xFFA855F7))
                    }
                }
            }

            // Překryvné okno při VÍTĚZSTVÍ (STAGE CLEAR), PROHŘE (GAME OVER) nebo PAUZE
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
                                    text = "STAGE CLEAR!",
                                    color = Color(0xFF4ADE80),
                                    fontFamily = FontFamily.Monospace,
                                    fontWeight = FontWeight.ExtraBold,
                                    fontSize = 22.sp,
                                    textAlign = TextAlign.Center
                                )
                                Text(
                                    text = "Vsechny viry v lahvi byly zlikvidovany!",
                                    color = Color(0xFFE2E8F0),
                                    fontFamily = FontFamily.Monospace,
                                    textAlign = TextAlign.Center,
                                    fontSize = 13.sp
                                )
                                OverlayActionButton("DALŠÍ LEVEL (${(currentLevel + 1).coerceAtMost(20)})", Color(0xFF22C55E)) {
                                    currentLevel = (currentLevel + 1).coerceAtMost(20)
                                    startStage(currentLevel)
                                }
                            }

                            PlayPhase.GAME_OVER -> {
                                Text(
                                    text = "GAME OVER",
                                    color = Color(0xFFEF4444),
                                    fontFamily = FontFamily.Monospace,
                                    fontWeight = FontWeight.ExtraBold,
                                    fontSize = 22.sp,
                                    textAlign = TextAlign.Center
                                )
                                Text(
                                    text = "Hrdlo lahve se ucpalo!\nZustalo viru: $totalViruses",
                                    color = Color(0xFFE2E8F0),
                                    fontFamily = FontFamily.Monospace,
                                    textAlign = TextAlign.Center,
                                    fontSize = 13.sp
                                )
                                OverlayActionButton("ZKUSIT ZNOVU", Color(0xFF3B82F6)) {
                                    score = 0
                                    startStage(currentLevel)
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

        // 3. SPODNÍ RETRO OVLÁDACÍ PANEL (NES D-Pad vlevo + kulatá tlačítka se směrem otáčení vpravo)
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
            // Vlevo: Směrový kříž + posun + rychlý pád
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                DPadButton("◀", widthDp = 46) { moveHorizontal(-1) }
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    DPadButton("▼", widthDp = 46) { stepDown() }
                    DPadButton("⏬", widthDp = 46) { hardDrop() }
                }
                DPadButton("▶", widthDp = 46) { moveHorizontal(1) }
            }

            // Vpravo: Kulatá tlačítka přímo se symbolem směru otáčení (doleva ↺ a doprava ↻)
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
            .padding(horizontal = 7.dp, vertical = 5.dp)
    ) {
        Text(
            text = label,
            color = Color.White,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Bold,
            fontSize = 11.sp,
            maxLines = 1
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
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(color)
            )
            Text(
                text = label,
                color = Color(0xFFCBD5E1),
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                fontSize = 11.sp
            )
        }
        Text(
            text = count.toString().padStart(2, '0'),
            color = Color.White,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Bold,
            fontSize = 11.sp
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
            fontSize = 10.sp
        )
        Text(
            text = value,
            color = accent,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.ExtraBold,
            fontSize = 14.sp
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
// PIXEL-ART VYKRESLOVÁNÍ (VIRY, SPOJENÉ KAPSLE, DOKTOR, EFEKTY)
// ============================================================================

private fun DrawScope.drawPixelVirus(
    topLeft: Offset,
    cellSize: Float,
    color: PillColor,
    frame: Int
) {
    // 8x8 pixelová matice klasického 8bitového viru (0=prázdno, 1=hlavní barva, 2=bílý obrys/oči, 3=zornička/pusa)
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
    val px = cellSize / 8f

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
                topLeft = Offset(topLeft.x + c * px, topLeft.y + r * px),
                size = Size(px + 0.5f, px + 0.5f)
            )
        }
    }
}

private fun DrawScope.drawCapsuleHalf(
    topLeft: Offset,
    cellSize: Float,
    color: PillColor,
    kind: CellKind
) {
    val pad = cellSize * 0.06f
    val x = topLeft.x + pad
    val y = topLeft.y + pad
    val w = cellSize - pad * 2f
    val h = cellSize - pad * 2f
    val r = w * 0.46f
    val border = 1.6f

    // 1. Vnější tmavý obrys (zaoblený základ + rovné napojení u spojené pilulky)
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
                size = Size((topLeft.x + cellSize) - (x + w * 0.5f), h)
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
                size = Size(w, (topLeft.y + cellSize) - (y + h * 0.5f))
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

    // 2. Vnitřní jasná výplň pilulky
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
                size = Size((topLeft.x + cellSize - 1f) - (x + w * 0.5f), h - border * 2f)
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
                size = Size(w - border * 2f, (topLeft.y + cellSize - 1f) - (y + h * 0.5f))
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

    // 3. 8bitový bílý odlesk na povrchu pilulky
    drawRoundRect(
        color = color.lightHex,
        topLeft = Offset(x + w * 0.20f, y + h * 0.18f),
        size = Size(w * 0.28f, h * 0.20f),
        cornerRadius = CornerRadius(3f, 3f)
    )
}

private fun DrawScope.drawClearingPop(
    topLeft: Offset,
    cellSize: Float,
    color: PillColor
) {
    val center = Offset(topLeft.x + cellSize / 2f, topLeft.y + cellSize / 2f)
    drawCircle(
        color = Color.White,
        radius = cellSize * 0.42f,
        center = center,
        style = Stroke(width = 3f)
    )
    drawCircle(
        color = color.lightHex,
        radius = cellSize * 0.22f,
        center = center
    )
}

private fun DrawScope.drawPixelDoctor(throwing: Boolean) {
    // 8x8 zjednodušený pixel-art Dr. Pilulky (zrcátko na čele, obličej, bílý plášť)
    val px = size.minDimension / 8f
    // Čelní lékařské zrcátko
    drawRect(Color(0xFF38BDF8), Offset(px * 3f, px * 0.5f), Size(px * 2f, px * 1.2f))
    // Hlava a vlasy
    drawRect(Color(0xFF78350F), Offset(px * 2f, px * 1.2f), Size(px * 4f, px * 1.2f))
    drawRect(Color(0xFFFDE68A), Offset(px * 2f, px * 2.2f), Size(px * 4f, px * 2.2f))
    // Oči a knír
    drawRect(Color(0xFF0F172A), Offset(px * 2.6f, px * 2.6f), Size(px * 0.7f, px * 0.7f))
    drawRect(Color(0xFF0F172A), Offset(px * 4.6f, px * 2.6f), Size(px * 0.7f, px * 0.7f))
    drawRect(Color(0xFF78350F), Offset(px * 2.5f, px * 3.6f), Size(px * 3f, px * 0.6f))
    // Bílý lékařský plášť
    drawRect(Color.White, Offset(px * 1.5f, px * 4.4f), Size(px * 5f, px * 3.0f))
    drawRect(Color(0xFFEF4444), Offset(px * 3.6f, px * 4.6f), Size(px * 0.8f, px * 2.0f))
    // Ruka házející kapsli
    val armY = if (throwing) px * 2.0f else px * 4.4f
    drawRect(Color.White, Offset(px * 0.4f, armY), Size(px * 1.2f, px * 1.8f))
}

// ============================================================================
// ČISTÁ HERNÍ LOGIKA (GENEROVÁNÍ VIRŮ, MATCH-4, ROZPAD KAPSLÍ A GRAVITACE)
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
    // Ve vyšších levelech mohou být viry o něco výš, ale horní řádky 0..3 zůstávají vždy volné pro manipulaci
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

        // Zabráníme tomu, aby už při generování vznikly 3 stejné viry v řadě
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

    // 1. Vodorovné sekvence 4 a více stejných barev
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

    // 2. Svislé sekvence 4 a více stejných barev
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

    // Odpojení zbylých polovin kapslí, jejichž dvojče bylo zničeno
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

    // Procházíme od předposledního řádku směrem nahoru
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
                    // Svislá kapsle: spodní dílek je v r, horní v r-1
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
                    // Vodorovná kapsle padá pouze tehdy, když je volno pod oběma polovinami
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
