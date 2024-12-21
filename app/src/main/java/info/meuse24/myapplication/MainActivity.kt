// File: MainActivity.kt
package info.meuse24.myapplication

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.changedToUp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import kotlin.random.Random

@Stable
data class ParticleSettings(
    val baseSpeed: Float = 150f,
    val fadeFactor: Float = 1.0f,
    val particleSize: Float = 10f,
    val emissionRate: Int = 3,
    val angleDeviation: Float = 15f
)

class GameState(
    private val playerParticleSystem: ParticleSystem = ParticleSystem(
        particleGroup = ParticleGroup.PLAYER
    ),
    private val topParticleSystem: ParticleSystem = ParticleSystem(
        particleColors = listOf(Color.Red),
        emissionRate = 2,
        particleSize = 5f,
        angleDeviation = 0f,
        baseSpeed = 100f,
        particleGroup = ParticleGroup.HORIZONTAL
    )
) {
    var playerPosition by mutableStateOf(Offset(200f, 200f))
    var isEmitting by mutableStateOf(false)
    private var emissionCounter = 0
    private var topEmissionCounter = 0

    val particleCount: Int
        get() = playerParticleSystem.getParticleCount() + topParticleSystem.getParticleCount()

    private fun setScreenDimensions(width: Float) {
        playerParticleSystem.setScreenDimensions(width)
        topParticleSystem.setScreenDimensions(width)
    }

    fun updateParameters(settings: ParticleSettings, newPlayerParticleColors: List<Color>?, topSettings: ParticleSettings, newTopParticleColors: List<Color>?) {
        playerParticleSystem.updateParameters(
            newBaseSpeed = settings.baseSpeed,
            newFadeFactor = settings.fadeFactor,
            newParticleSize = settings.particleSize,
            newEmissionRate = settings.emissionRate,
            newAngleDeviation = settings.angleDeviation,
            newParticleColors = newPlayerParticleColors
        )
        topParticleSystem.updateParameters(
            newBaseSpeed = topSettings.baseSpeed,
            newFadeFactor = topSettings.fadeFactor,
            newParticleSize = topSettings.particleSize,
            newEmissionRate = topSettings.emissionRate,
            newAngleDeviation = topSettings.angleDeviation,
            newParticleColors = newTopParticleColors
        )
    }

    fun update(deltaTime: Float, screenWidth: Float, screenHeight:Float) {
        setScreenDimensions(screenWidth)
        // Update Spieler-Partikelsystem
        playerParticleSystem.updateParticles(deltaTime)

        if (isEmitting) {
            emissionCounter++
            if (emissionCounter > 5) {
                emissionCounter = 0
                repeat(playerParticleSystem.emissionRate) {
                    playerParticleSystem.spawnParticle(
                        position = playerPosition
                    )
                }
            }
        } else {
            emissionCounter = 0
        }

        topParticleSystem.updateParticles(deltaTime)

        topEmissionCounter++
        if (topEmissionCounter > 60) {
            topEmissionCounter = 0
            val goRight = Random.nextBoolean()
            val direction = if (goRight) 1f else -1f
            val xPos = if (goRight) 0f else screenWidth
            val yPos = (screenHeight / 2f) + Random.nextFloat() * 400f - 200f
            val velocity = Offset(direction * topParticleSystem.baseSpeed, 0f)

            topParticleSystem.spawnParticle(position = Offset(xPos, yPos), velocity = velocity)
        }

        // Kollisionen prüfen nach dem Update der Partikelpositionen
        checkCollisions()
    }

    private fun checkParticleCollision(p1: ParticleData, p2: ParticleData): Boolean {
        // Schneller Vorabcheck mit AABB (Axis-Aligned Bounding Box)
        val combinedSize = p1.size + p2.size
        if (kotlin.math.abs(p1.position.x - p2.position.x) > combinedSize ||
            kotlin.math.abs(p1.position.y - p2.position.y) > combinedSize) {
            return false
        }

        // Quadrierte Distanz berechnen (vermeidet teure Quadratwurzel)
        val dx = p1.position.x - p2.position.x
        val dy = p1.position.y - p2.position.y
        val distanceSquared = dx * dx + dy * dy

        // Quadrierter Schwellwert für Vergleich
        val thresholdSquared = combinedSize * combinedSize

        return distanceSquared < thresholdSquared
    }

    private fun checkCollisions() {
        val horizontalParticles = getParticles().filter { it.group == ParticleGroup.HORIZONTAL }
        val playerParticles = getParticles().filter { it.group == ParticleGroup.PLAYER }

        for (horizontal in horizontalParticles) {
            for (player in playerParticles) {
                if (checkParticleCollision(horizontal, player)) {
                    // Hier können wir auf die Kollision reagieren
                    // Zum Beispiel:
                    handleCollision(horizontal, player)
                }
            }
        }
    }

    private fun handleCollision(horizontal: ParticleData, player: ParticleData) {
        topParticleSystem.deactivateParticle(horizontal)
        playerParticleSystem.deactivateParticle(player)
        //collisionCount++
    }

    fun getParticles(): List<ParticleData> = playerParticleSystem.getActiveParticles() + topParticleSystem.getActiveParticles()

}


class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MyDraggableCircleApp()
        }
    }
}

@Composable
fun MyDraggableCircleApp() {
    MaterialTheme {
        Surface(modifier = Modifier.fillMaxSize()) {
            GameScreen()
        }
    }
}

@Composable
fun GameScreen(
    backgroundColor: Color = Color.Black,
    playerColor: Color = Color.Red
) {
    var showPlayerSettings by remember { mutableStateOf(false) }
    var showTopSettings by remember { mutableStateOf(false) }

    var playerSettings by remember { mutableStateOf(ParticleSettings()) }
    var topSettings by remember { mutableStateOf(ParticleSettings(
        baseSpeed = 100f,
        fadeFactor = 1.0f,
        particleSize = 5f,
        emissionRate = 2,
        angleDeviation = 0f
    )) }

    val availableColors = listOf(
        Color.Green, Color.Cyan, Color.Magenta,
        Color.Yellow, Color.Blue, Color.White, Color.Red
    )
    var selectedPlayerColors by remember { mutableStateOf(listOf(Color.Green, Color.Cyan, Color.Magenta, Color.Yellow, Color.Blue, Color.White)) }
    var selectedTopColors by remember { mutableStateOf(listOf(Color.Red)) }

    val particles = remember { mutableStateListOf<ParticleData>() }
    var particleCount by remember { mutableIntStateOf(0) }

    val gameState = remember { GameState() }

    LaunchedEffect(playerSettings, selectedPlayerColors, topSettings, selectedTopColors) {
        gameState.updateParameters(
            settings = playerSettings,
            newPlayerParticleColors = selectedPlayerColors,
            topSettings = topSettings,
            newTopParticleColors = selectedTopColors
        )
    }

    BoxWithConstraints(
        modifier = Modifier.fillMaxSize()
    ) {
        val density = LocalDensity.current
        val screenWidthPx = with(density) { maxWidth.toPx() }
        val screenHeightPx = with(density) { maxHeight.toPx() }

        val initialPlayerPosition = remember(maxWidth, maxHeight) {
            Offset(
                x = screenWidthPx / 2,
                y = screenHeightPx - 30f
            )
        }

        var playerPosition by remember { mutableStateOf(initialPlayerPosition) }
        var isEmitting by remember { mutableStateOf(false) }

        LaunchedEffect(Unit) {
            withContext(Dispatchers.Default) {
                var previousTime = 0L
                while (isActive) {
                    val currentTime = withFrameNanos { it }
                    if (previousTime != 0L) {
                        val deltaTime = (currentTime - previousTime) / 1_000_000_000f

                        gameState.playerPosition = playerPosition
                        gameState.isEmitting = isEmitting
                        gameState.update(deltaTime, screenWidthPx, screenHeightPx)

                        val activeParticles = gameState.getParticles()
                        val currentParticleCount = gameState.particleCount

                        withContext(Dispatchers.Main) {
                            particles.clear()
                            particles.addAll(activeParticles)
                            particleCount = currentParticleCount
                        }
                    }
                    previousTime = currentTime
                }
            }
        }

        Box(modifier = Modifier.fillMaxSize()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(backgroundColor)
                    .pointerInput(Unit) {
                        while (true) {
                            val down = awaitPointerEventScope { awaitFirstDown() }
                            isEmitting = true

                            awaitPointerEventScope {
                                val pointerId = down.id
                                while (true) {
                                    val event = awaitPointerEvent()
                                    val change = event.changes.find { it.id == pointerId }

                                    if (change == null || change.changedToUp()) {
                                        isEmitting = false
                                        break
                                    } else {
                                        val delta = change.positionChange()
                                        if (delta != Offset.Zero) {
                                            playerPosition = Offset(
                                                x = playerPosition.x + delta.x,
                                                y = playerPosition.y + delta.y
                                            )
                                            change.consume()
                                        }
                                    }
                                }
                            }
                        }
                    }
            ) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    particles.forEach { particle ->
                        drawCircle(
                            color = particle.color.copy(alpha = particle.alpha),
                            radius = particle.size,
                            center = particle.position
                        )
                    }

                    drawCircle(
                        color = playerColor,
                        radius = 50f,
                        center = playerPosition
                    )
                }
            }

            // Button für Spieler-Partikel Einstellungen
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(16.dp)
            ) {
                IconButton(
                    onClick = { showTopSettings = true },
                    modifier = Modifier
                        .size(48.dp)
                        .background(
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.7f),
                            CircleShape
                        )
                ) {
                    Icon(
                        imageVector = Icons.Default.Settings,
                        contentDescription = "Spieler Einstellungen",
                        tint = Color.White
                    )
                }
            }

            // Button für Top-Partikel Einstellungen
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(16.dp)
            ) {
                IconButton(
                    onClick = { showPlayerSettings = true },
                    modifier = Modifier
                        .size(48.dp)
                        .background(
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.7f),
                            CircleShape
                        )
                ) {
                    Icon(
                        imageVector = Icons.Default.Settings,
                        contentDescription = "Obere Partikel Einstellungen",
                        tint = Color.White
                    )
                }
            }

// Partikelanzahl und FPS anzeigen
            Box(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(16.dp)
            ) {
                var fps by remember { mutableIntStateOf(0) }
                var frameCount by remember { mutableIntStateOf(0) }
                var lastFpsUpdate by remember { androidx.compose.runtime.mutableLongStateOf(0L) }

                LaunchedEffect(Unit) {
                    while (true) {
                        withFrameNanos { frameTime ->
                            frameCount++
                            val elapsed = frameTime - lastFpsUpdate

                            if (elapsed >= 1_000_000_000) { // Update FPS every second
                                fps = frameCount
                                frameCount = 0
                                lastFpsUpdate = frameTime
                            }
                        }
                    }
                }

                Column {
                    Text(
                        text = "Partikel: $particleCount | FPS: $fps",
                        color = Color.White,
                        modifier = Modifier
                            .background(
                                color = Color.Black.copy(alpha = 0.5f),
                                shape = RoundedCornerShape(4.dp)
                            )
                            .padding(8.dp)
                    )
                }
            }

// Spieler Partikel Einstellungen Dialog
            if (showPlayerSettings) {
                var tempSelectedColors by remember { mutableStateOf(selectedPlayerColors.toList()) }
                var tempPlayerSettings by remember { mutableStateOf(playerSettings) }

                AlertDialog(
                    onDismissRequest = { showPlayerSettings = false },
                    title = {
                        Text(
                            "Spieler Partikel-Einstellungen",
                            style = MaterialTheme.typography.headlineSmall
                        )
                    },
                    text = {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .verticalScroll(rememberScrollState())
                                .padding(16.dp)
                                //.height(IntrinsicSize.Min)
                                //.heightIn(max = 400.dp)
                        ) {
                            Text(
                                text = "Basis-Geschwindigkeit: ${tempPlayerSettings.baseSpeed.toInt()}",
                                style = MaterialTheme.typography.bodyLarge
                            )
                            Slider(
                                value = tempPlayerSettings.baseSpeed,
                                onValueChange = {
                                    tempPlayerSettings = tempPlayerSettings.copy(baseSpeed = it)
                                },
                                valueRange = 50f..300f,
                                modifier = Modifier.padding(vertical = 8.dp)
                            )

                            Spacer(modifier = Modifier.height(16.dp))

                            Text(
                                text = "Verblassungsfaktor: ${"%.2f".format(tempPlayerSettings.fadeFactor)}",
                                style = MaterialTheme.typography.bodyLarge
                            )
                            Slider(
                                value = tempPlayerSettings.fadeFactor,
                                onValueChange = {
                                    tempPlayerSettings = tempPlayerSettings.copy(fadeFactor = it)
                                },
                                valueRange = 0.5f..3.0f,
                                modifier = Modifier.padding(vertical = 8.dp)
                            )

                            Spacer(modifier = Modifier.height(16.dp))

                            Text(
                                text = "Partikelgröße: ${tempPlayerSettings.particleSize.toInt()}",
                                style = MaterialTheme.typography.bodyLarge
                            )
                            Slider(
                                value = tempPlayerSettings.particleSize,
                                onValueChange = {
                                    tempPlayerSettings = tempPlayerSettings.copy(particleSize = it)
                                },
                                valueRange = 5f..30f,
                                modifier = Modifier.padding(vertical = 8.dp)
                            )

                            Spacer(modifier = Modifier.height(16.dp))

                            Text(
                                text = "Emissionsrate: ${tempPlayerSettings.emissionRate}",
                                style = MaterialTheme.typography.bodyLarge
                            )
                            Slider(
                                value = tempPlayerSettings.emissionRate.toFloat(),
                                onValueChange = {
                                    tempPlayerSettings = tempPlayerSettings.copy(emissionRate = it.toInt())
                                },
                                valueRange = 1f..10f,
                                steps = 8,
                                modifier = Modifier.padding(vertical = 8.dp)
                            )

                            Spacer(modifier = Modifier.height(16.dp))

                            Text(
                                text = "Winkelabweichung: ${tempPlayerSettings.angleDeviation.toInt()}°",
                                style = MaterialTheme.typography.bodyLarge
                            )
                            Slider(
                                value = tempPlayerSettings.angleDeviation,
                                onValueChange = {
                                    tempPlayerSettings = tempPlayerSettings.copy(angleDeviation = it)
                                },
                                valueRange = 0f..45f,
                                modifier = Modifier.padding(vertical = 8.dp)
                            )

                            Spacer(modifier = Modifier.height(16.dp))

                            Text(
                                text = "Partikelfarben",
                                style = MaterialTheme.typography.bodyLarge
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .horizontalScroll(rememberScrollState())
                            ) {
                                availableColors.filter { it != Color.Red }.forEach { color ->
                                    val isSelected = tempSelectedColors.contains(color)
                                    Column(
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        modifier = Modifier
                                            .padding(end = 8.dp)
                                            .clickable {
                                                tempSelectedColors = if (isSelected) {
                                                    tempSelectedColors - color
                                                } else {
                                                    tempSelectedColors + color
                                                }
                                            }
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(40.dp)
                                                .background(color, CircleShape)
                                                .border(
                                                    width = if (isSelected) 3.dp else 1.dp,
                                                    color = if (isSelected) Color.White else Color.Gray,
                                                    shape = CircleShape
                                                )
                                        )
                                        Checkbox(
                                            checked = isSelected,
                                            onCheckedChange = {
                                                tempSelectedColors = if (isSelected) {
                                                    tempSelectedColors - color
                                                } else {
                                                    tempSelectedColors + color
                                                }
                                            }
                                        )
                                    }
                                }
                            }
                        }
                    },
                    confirmButton = {
                        TextButton(onClick = {
                            selectedPlayerColors = tempSelectedColors
                            playerSettings = tempPlayerSettings
                            showPlayerSettings = false
                        }) {
                            Text("Anwenden")
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = {
                            showPlayerSettings = false
                        }) {
                            Text("Abbrechen")
                        }
                    }
                )
            }
// Top Partikel Einstellungen Dialog
            if (showTopSettings) {
                var tempSelectedColors by remember { mutableStateOf(selectedTopColors.toList()) }
                var tempTopSettings by remember { mutableStateOf(topSettings) }

                AlertDialog(
                    onDismissRequest = { showTopSettings = false },
                    title = {
                        Text(
                            "Obere Partikel-Einstellungen",
                            style = MaterialTheme.typography.headlineSmall
                        )
                    },
                    text = {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .verticalScroll(rememberScrollState())
                                .padding(16.dp)
                                //.height(IntrinsicSize.Min)
                                //.heightIn(max = 400.dp)
                        ) {
                            Text(
                                text = "Basis-Geschwindigkeit: ${tempTopSettings.baseSpeed.toInt()}",
                                style = MaterialTheme.typography.bodyLarge
                            )
                            Slider(
                                value = tempTopSettings.baseSpeed,
                                onValueChange = {
                                    tempTopSettings = tempTopSettings.copy(baseSpeed = it)
                                },
                                valueRange = 50f..300f,
                                modifier = Modifier.padding(vertical = 8.dp)
                            )

                            Spacer(modifier = Modifier.height(16.dp))

                            Text(
                                text = "Verblassungsfaktor: ${"%.2f".format(tempTopSettings.fadeFactor)}",
                                style = MaterialTheme.typography.bodyLarge
                            )
                            Slider(
                                value = tempTopSettings.fadeFactor,
                                onValueChange = {
                                    tempTopSettings = tempTopSettings.copy(fadeFactor = it)
                                },
                                valueRange = 0.5f..3.0f,
                                modifier = Modifier.padding(vertical = 8.dp)
                            )

                            Spacer(modifier = Modifier.height(16.dp))

                            Text(
                                text = "Partikelgröße: ${tempTopSettings.particleSize.toInt()}",
                                style = MaterialTheme.typography.bodyLarge
                            )
                            Slider(
                                value = tempTopSettings.particleSize,
                                onValueChange = {
                                    tempTopSettings = tempTopSettings.copy(particleSize = it)
                                },
                                valueRange = 5f..30f,
                                modifier = Modifier.padding(vertical = 8.dp)
                            )

                            Spacer(modifier = Modifier.height(16.dp))

                            Text(
                                text = "Emissionsrate: ${tempTopSettings.emissionRate}",
                                style = MaterialTheme.typography.bodyLarge
                            )
                            Slider(
                                value = tempTopSettings.emissionRate.toFloat(),
                                onValueChange = {
                                    tempTopSettings = tempTopSettings.copy(emissionRate = it.toInt())
                                },
                                valueRange = 1f..10f,
                                steps = 8,
                                modifier = Modifier.padding(vertical = 8.dp)
                            )

                            Spacer(modifier = Modifier.height(16.dp))

                            Text(
                                text = "Winkelabweichung: ${tempTopSettings.angleDeviation.toInt()}°",
                                style = MaterialTheme.typography.bodyLarge
                            )
                            Slider(
                                value = tempTopSettings.angleDeviation,
                                onValueChange = {
                                    tempTopSettings = tempTopSettings.copy(angleDeviation = it)
                                },
                                valueRange = 0f..45f,
                                modifier = Modifier.padding(vertical = 8.dp)
                            )

                            Spacer(modifier = Modifier.height(16.dp))

                            Text(
                                text = "Partikelfarben",
                                style = MaterialTheme.typography.bodyLarge
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .horizontalScroll(rememberScrollState())
                            ) {
                                availableColors.filter { it == Color.Red }.forEach { color ->
                                    val isSelected = tempSelectedColors.contains(color)
                                    Column(
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        modifier = Modifier
                                            .padding(end = 8.dp)
                                            .clickable {
                                                tempSelectedColors = if (isSelected) {
                                                    tempSelectedColors - color
                                                } else {
                                                    tempSelectedColors + color
                                                }
                                            }
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(40.dp)
                                                .background(color, CircleShape)
                                                .border(
                                                    width = if (isSelected) 3.dp else 1.dp,
                                                    color = if (isSelected) Color.White else Color.Gray,
                                                    shape = CircleShape
                                                )
                                        )
                                        Checkbox(
                                            checked = isSelected,
                                            onCheckedChange = {
                                                tempSelectedColors = if (isSelected) {
                                                    tempSelectedColors - color
                                                } else {
                                                    tempSelectedColors + color
                                                }
                                            }
                                        )
                                    }
                                }
                            }
                        }
                    },
                    confirmButton = {
                        TextButton(onClick = {
                            selectedTopColors = tempSelectedColors
                            topSettings = tempTopSettings
                            showTopSettings = false
                        }) {
                            Text("Anwenden")
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = {
                            showTopSettings = false
                        }) {
                            Text("Abbrechen")
                        }
                    }
                )
            }
        }
    }
}
