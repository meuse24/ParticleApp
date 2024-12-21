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
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.horizontalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.changedToUp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext

@Stable
data class ParticleSettings(
    val baseSpeed: Float = 150f,
    val fadeFactor: Float = 1.0f,
    val particleSize: Float = 10f,
    val emissionRate: Int = 3,
    val angleDeviation: Float = 15f
)

class GameState(private val particleSystem: ParticleSystem = ParticleSystem()) {
    var playerPosition by mutableStateOf(Offset(200f, 200f))
    var isEmitting by mutableStateOf(false)
    private var emissionCounter = 0

    val particleCount: Int
        get() = particleSystem.getParticleCount()

    fun updateParameters(settings: ParticleSettings, newParticleColors: List<Color>?) {
        particleSystem.updateParameters(
            newBaseSpeed = settings.baseSpeed,
            newFadeFactor = settings.fadeFactor,
            newParticleSize = settings.particleSize,
            newEmissionRate = settings.emissionRate,
            newAngleDeviation = settings.angleDeviation,
            newParticleColors = newParticleColors
        )
    }

    fun update(deltaTime: Float) {
        particleSystem.updateParticles(deltaTime)

        if (isEmitting) {
            emissionCounter++
            if (emissionCounter > 5) {
                emissionCounter = 0
                repeat(particleSystem.emissionRate) {
                    particleSystem.spawnParticle(
                        position = playerPosition
                    )
                }
            }
        } else {
            emissionCounter = 0
        }
    }

    fun getParticles(): List<ParticleData> = particleSystem.getActiveParticles()
    fun getParticleSize(): Float = particleSystem.getParticleSize()
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
    var showSettings by remember { mutableStateOf(false) }

    var particleSettings by remember { mutableStateOf(ParticleSettings()) }

    val availableColors = listOf(
        Color.Green, Color.Cyan, Color.Magenta,
        Color.Yellow, Color.Blue, Color.White
    )
    var selectedColors by remember { mutableStateOf(availableColors.toList()) }

    val particles = remember { mutableStateListOf<ParticleData>() }
    var particleCount by remember { mutableIntStateOf(0) }

    val gameState = remember { GameState(ParticleSystem(particleColors = selectedColors)) }

    LaunchedEffect(particleSettings, selectedColors) {
        gameState.updateParameters(particleSettings, selectedColors)
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
                        gameState.update(deltaTime)

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
                            radius = gameState.getParticleSize(),
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

            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(16.dp)
            ) {
                IconButton(
                    onClick = { showSettings = true },
                    modifier = Modifier
                        .size(48.dp)
                        .background(
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.7f),
                            CircleShape
                        )
                ) {
                    Icon(
                        imageVector = Icons.Default.Settings,
                        contentDescription = "Einstellungen",
                        tint = Color.White
                    )
                }
            }

            Box(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(16.dp)
            ) {
                Text(
                    text = "Partikel: $particleCount",
                    color = Color.White,
                    modifier = Modifier
                        .background(
                            color = Color.Black.copy(alpha = 0.5f),
                            shape = RoundedCornerShape(4.dp)
                        )
                        .padding(8.dp)
                )
            }

            if (showSettings) {
                var tempSelectedColors by remember { mutableStateOf(selectedColors.toList()) }

                AlertDialog(
                    onDismissRequest = { showSettings = false },
                    title = {
                        Text(
                            "Partikel-Einstellungen",
                            style = MaterialTheme.typography.headlineSmall
                        )
                    },
                    text = {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp)
                        ) {
                            Text(
                                text = "Basis-Geschwindigkeit: ${particleSettings.baseSpeed.toInt()}",
                                style = MaterialTheme.typography.bodyLarge
                            )
                            Slider(
                                value = particleSettings.baseSpeed,
                                onValueChange = {
                                    particleSettings = particleSettings.copy(baseSpeed = it)
                                },
                                valueRange = 50f..300f,
                                modifier = Modifier.padding(vertical = 8.dp)
                            )

                            Spacer(modifier = Modifier.height(16.dp))

                            Text(
                                text = "Verblassungsfaktor: ${"%.2f".format(particleSettings.fadeFactor)}",
                                style = MaterialTheme.typography.bodyLarge
                            )
                            Slider(
                                value = particleSettings.fadeFactor,
                                onValueChange = {
                                    particleSettings = particleSettings.copy(fadeFactor = it)
                                },
                                valueRange = 0.5f..3.0f,
                                modifier = Modifier.padding(vertical = 8.dp)
                            )

                            Spacer(modifier = Modifier.height(16.dp))

                            Text(
                                text = "Partikelgröße: ${particleSettings.particleSize.toInt()}",
                                style = MaterialTheme.typography.bodyLarge
                            )
                            Slider(
                                value = particleSettings.particleSize,
                                onValueChange = {
                                    particleSettings = particleSettings.copy(particleSize = it)
                                },
                                valueRange = 5f..30f,
                                modifier = Modifier.padding(vertical = 8.dp)
                            )

                            Spacer(modifier = Modifier.height(16.dp))

                            Text(
                                text = "Emissionsrate: ${particleSettings.emissionRate}",
                                style = MaterialTheme.typography.bodyLarge
                            )
                            Slider(
                                value = particleSettings.emissionRate.toFloat(),
                                onValueChange = {
                                    particleSettings = particleSettings.copy(emissionRate = it.toInt())
                                },
                                valueRange = 1f..10f,
                                steps = 8,
                                modifier = Modifier.padding(vertical = 8.dp)
                            )

                            Spacer(modifier = Modifier.height(16.dp))

                            Text(
                                text = "Winkelabweichung: ${particleSettings.angleDeviation.toInt()}°",
                                style = MaterialTheme.typography.bodyLarge
                            )
                            Slider(
                                value = particleSettings.angleDeviation,
                                onValueChange = {
                                    particleSettings = particleSettings.copy(angleDeviation = it)
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
                                availableColors.forEach { color ->
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
                            selectedColors = tempSelectedColors
                            showSettings = false
                        }) {
                            Text("Anwenden")
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = {
                            showSettings = false
                        }) {
                            Text("Abbrechen")
                        }
                    }
                )
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
fun PreviewGameScreen() {
    MyDraggableCircleApp()
}
