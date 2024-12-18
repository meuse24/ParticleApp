package info.meuse24.myapplication

import android.annotation.SuppressLint
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.changedToUp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.random.Random

// Datenklasse für Partikel.
// Jedes Partikel hat:
// - position: Die aktuelle Position im Raum (in Pixelkoordinaten)
// - velocity: Die Geschwindigkeit (x, y), beeinflusst wie das Partikel pro Frame verschoben wird
// - startY: Der Y-Wert, an dem das Partikel entstanden ist, wird für die Alpha-Berechnung genutzt.
// - alpha: Transparenzwert zwischen 0 (unsichtbar) und 1 (voll sichtbar).
// - color: Die Farbe dieses Partikels.
// - fadeFactor: Bestimmt, wie schnell das Partikel ausblendet (höherer Wert = schnelleres Verblassen).
data class ParticleData(
    var position: Offset,
    var velocity: Offset,
    var startY: Float,
    var alpha: Float = 1f,
    var color: Color,
    var fadeFactor: Float
)

// ParticlePool ist ein Ressourcen-Manager für Partikel.
// Er hält aktive und inaktive Partikel vor, um unnötige Objekt-Neuerstellungen zu vermeiden.
// Dadurch kann die Performance gesteigert werden, da weniger Garbage Collection nötig ist.
class ParticlePool(private val maxSize: Int = 1000) {
    private val activeParticles = mutableListOf<ParticleData>()
    private val inactiveParticles = mutableListOf<ParticleData>()

    val particleCount: Int
        get() = activeParticles.size

    // Liste möglicher Farben für Partikel
    private val particleColors = listOf(
        Color.Green,
        Color.Cyan,
        Color.Magenta,
        Color.Yellow,
        Color.Blue,
        Color.White
    )

    // spawn erzeugt oder reaktiviert ein Partikel mit zufälliger Farbe und Zufallswerten.
    // position: Startposition des Partikels
    // velocity: Geschwindigkeit des Partikels
    fun spawn(position: Offset, velocity: Offset): ParticleData {
        // Zufällige Farbe auswählen
        val chosenColor = particleColors.random()
        // Zufälliger Fade-Faktor zwischen 0.5 und 2.0
        // Partikel mit höherem Wert verblassen schneller.
        val fadeFactor = 0.5f + Random.nextFloat() * 1.5f

        // Wenn bereits die maximale Anzahl an aktiven Partikeln erreicht ist
        // und kein inaktives Partikel vorhanden ist, recyceln wir das erste aktive Partikel.
        if (activeParticles.size >= maxSize && inactiveParticles.isEmpty()) {
            val recycled = activeParticles.removeAt(0)
            recycled.position = position
            recycled.velocity = velocity
            recycled.startY = position.y
            recycled.alpha = 1f
            recycled.color = chosenColor
            recycled.fadeFactor = fadeFactor
            activeParticles.add(recycled)
            return recycled
        }

        // Falls es inaktive Partikel gibt, reaktiviere eines davon.
        val particle = if (inactiveParticles.isNotEmpty()) {
            val p = inactiveParticles.removeAt(0)
            p.position = position
            p.velocity = velocity
            p.startY = position.y
            p.alpha = 1f
            p.color = chosenColor
            p.fadeFactor = fadeFactor
            p
        } else {
            // Neues Partikel erstellen, falls es keine inaktiven gibt.
            ParticleData(
                position = position,
                velocity = velocity,
                startY = position.y,
                alpha = 1f,
                color = chosenColor,
                fadeFactor = fadeFactor
            )
        }

        activeParticles.add(particle)
        return particle
    }

    // Update aktualisiert alle aktiven Partikel pro Frame.
    // deltaTime: Zeit seit dem letzten Frame in Sekunden
    // screenHeight: Höhe des verfügbaren Bereichs in Pixeln
    fun update(deltaTime: Float) {
        val iterator = activeParticles.iterator()
        while (iterator.hasNext()) {
            val particle = iterator.next()

            // Berechne, wie weit das Partikel aufgestiegen ist.
            val traveled = particle.startY - particle.position.y
            // Berechne Alpha-Wert unter Einbezug des fadeFactors.
            val rawAlpha = 1f - (traveled / particle.startY) * particle.fadeFactor
            particle.alpha = rawAlpha.coerceIn(0f, 1f)

            // Wenn das Partikel aus dem sichtbaren Bereich oben raus ist (y < -20f), entfernen.
            if (particle.position.y < -20f) {
                iterator.remove()
                inactiveParticles.add(particle)
                continue
            }

            // Positionsupdate: Verschiebung gemäß velocity und vergangener Zeit.
            particle.position = Offset(
                x = particle.position.x + particle.velocity.x * deltaTime,
                y = particle.position.y + particle.velocity.y * deltaTime
            )
        }
    }

    // Gibt die Liste der aktiven Partikel zurück, um sie im Canvas zu zeichnen.
    fun getActiveParticles(): List<ParticleData> = activeParticles
}

// GameState kümmert sich um den Spielzustand:
// - Position des "Spielers" (roter Kreis)
// - Erzeugen von Partikeln (isEmitting = true, wenn Finger auf dem Bildschirm)
// - Update des Partikelpools
class GameState {
    private val particlePool = ParticlePool()
    var playerPosition = Offset(200f, 200f)
    var isEmitting = false
    private var emissionCounter = 0

    val particleCount: Int
        get() = particlePool.particleCount

    // Update führt die logische Aktualisierung pro Frame aus.
    // deltaTime: Zeit seit letztem Frame
    // screenHeight: tatsächliche Bildschirmhöhe in Pixel
    fun update(deltaTime: Float) {
        // Partikel aktualisieren
        particlePool.update(deltaTime)

        // Wenn wir emitten (Finger auf dem Bildschirm), alle 5 Frames 3 neue Partikel erzeugen.
        if (isEmitting) {
            emissionCounter++
            if (emissionCounter > 5) {
                emissionCounter = 0
                repeat(3) {
                    val randomX = Random.nextFloat() * 80 - 40
                    val randomY = -(150f + Random.nextFloat() * 100f)
                    particlePool.spawn(
                        position = playerPosition,
                        velocity = Offset(randomX, randomY)
                    )
                }
            }
        } else {
            // Wenn nicht emittiert wird, Zähler zurücksetzen.
            emissionCounter = 0
        }
    }

    // Liste aktiver Partikel anfordern, um diese zeichnen zu können.
    fun getParticles(): List<ParticleData> = particlePool.getActiveParticles()
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

@SuppressLint("MultipleAwaitPointerEventScopes", "ReturnFromAwaitPointerEventScope")
@Composable
fun GameScreen(
    backgroundColor: Color = Color.Black,
    playerColor: Color = Color.Red,
) {
    // State-Variablen für die Spielerposition, Partikel, Emissionsstatus und Anzahl
    // von Partikeln. Alle als Compose States, damit Änderungen automatisch zum Neuzeichnen führen.
    var playerPosition by remember { mutableStateOf(Offset.Zero) }
    val particles = remember { mutableStateListOf<ParticleData>() }
    var isEmitting by remember { mutableStateOf(false) }
    var particleCount by remember { mutableIntStateOf(0) }

    // Erstellt einen gemeinsamen GameState, der die Logik verwaltet.
    val gameState = remember { GameState() }

    // BoxWithConstraints gibt uns maxWidth und maxHeight in Dp,
    // damit wir dynamisch Layout-Informationen erhalten.
    BoxWithConstraints(
        modifier = Modifier.fillMaxSize()
    ) {
        // LocalDensity erlaubt uns die Umrechnung von Dp in Pixel.
        val density = LocalDensity.current
        val screenWidthPx = with(density) { maxWidth.toPx() }
        val screenHeightPx = with(density) { maxHeight.toPx() }

        // Zu Beginn soll der rote Kreis horizontal mittig und 30 Pixel über dem unteren Rand stehen.
        // Wir führen dies in einem LaunchedEffect aus, damit es einmalig bei Start gesetzt wird,
        // wenn die Bildschirmmaße bekannt sind.
        LaunchedEffect(maxWidth, maxHeight) {
            playerPosition = Offset(
                x = screenWidthPx / 2,          // Horizontal mittig
                y = screenHeightPx - 30f        // 30 Pixel vom unteren Rand entfernt
            )
        }

        // Game Loop:
        // withFrameNanos wird pro Frame aufgerufen.
        // Wir berechnen deltaTime und führen dann Updates auf dem Hintergrund-Thread aus.
        LaunchedEffect(Unit) {
            var previousTime = 0L
            while (true) {
                val currentTime = withFrameNanos { it }
                if (previousTime != 0L) {
                    val deltaTime = (currentTime - previousTime) / 1_000_000_000f

                    val result = withContext(Dispatchers.Default) {
                        // Spielerposition und Emissionsstatus an GameState übergeben
                        gameState.playerPosition = playerPosition
                        gameState.isEmitting = isEmitting
                        // Spielzustand updaten
                        gameState.update(deltaTime)
                        // Ergebnis als Paar zurückgeben: Liste der Partikel und Anzahl
                        Pair(gameState.getParticles(), gameState.particleCount)
                    }

                    // Partikel-Liste im UI-Thread aktualisieren, damit sie gezeichnet wird.
                    particles.clear()
                    particles.addAll(result.first)
                    particleCount = result.second
                }
                previousTime = currentTime
            }
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(backgroundColor)
        ) {
            // Anzeige der aktiven Partikelanzahl oben im Bild
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                color = Color.Transparent
            ) {
                Text(
                    text = "Aktive Partikel: $particleCount",
                    color = Color.White,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            // Pointer-Input für den Spieler:
            // - Wenn der Finger auf dem Bildschirm aufsetzt (awaitFirstDown), beginnt das Emittieren.
            // - Während der Finger bewegt wird, ändern wir die Spielerposition.
            // - Wenn der Finger losgelassen wird, hört das Emittieren auf.
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(Unit) {
                        while (true) {
                            // Warten auf den ersten Finger-Kontakt mit dem Bildschirm
                            val down = awaitPointerEventScope { awaitFirstDown() }
                            isEmitting = true

                            // Solange der Finger auf dem Bildschirm ist, Bewegung verfolgen
                            awaitPointerEventScope {
                                val pointerId = down.id
                                while (true) {
                                    val event = awaitPointerEvent()
                                    val change = event.changes.find { it.id == pointerId }

                                    // Wenn Finger hoch geht, isEmitting = false
                                    if (change == null || change.changedToUp()) {
                                        isEmitting = false
                                        break
                                    } else {
                                        // Finger bewegt sich, passe Spielerposition an
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
                // Canvas zum Zeichnen der Partikel und des roten Kreises.
                Canvas(modifier = Modifier.fillMaxSize()) {
                    // Alle Partikel zeichnen
                    particles.forEach { particle ->
                        drawCircle(
                            color = particle.color.copy(alpha = particle.alpha),
                            radius = 10f,
                            center = particle.position
                        )
                    }

                    // Roten Kreis zeichnen (Spieler)
                    drawCircle(
                        color = playerColor,
                        radius = 50f,
                        center = playerPosition
                    )
                }
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
fun PreviewGameScreen() {
    MyDraggableCircleApp()
}






