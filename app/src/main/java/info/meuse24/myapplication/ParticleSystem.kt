// File: ParticleSystem.kt
package info.meuse24.myapplication

import androidx.compose.runtime.Stable
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

enum class ParticleGroup {
    PLAYER,
    HORIZONTAL,
    // Hier können später weitere Gruppen hinzugefügt werden
}
@Stable
data class ParticleData(
    var position: Offset,
    var velocity: Offset,
    var startY: Float,
    var alpha: Float = 1f,
    var color: Color,
    var fadeFactor: Float,
    var group: ParticleGroup,
    var size: Float  // Neue Property
)

class ParticlePool(
    private val maxSize: Int = 1000,
    private var fadeFactor: Float = 1.0f,
    var particleColors: List<Color>,
    private val particleGroup: ParticleGroup  // Neue Property
) {
    private var screenWidth: Float = 1000f  // Neue Variable
    private val activeParticles = ArrayList<ParticleData>(1000)
    private val inactiveParticles = ArrayList<ParticleData>(1000)
    val particleCount: Int get() = activeParticles.size
    private var currentParticleSize: Float = 10f  // Neue Property

    fun updateParticleSize(newSize: Float) {
        currentParticleSize = newSize
    }

    fun updateParameters(fadeFactor: Float? = null, newParticleColors: List<Color>? = null) {
        fadeFactor?.let { this.fadeFactor = it }
        newParticleColors?.let { this.particleColors = it }
    }

    fun setScreenDimensions(width: Float) {
        screenWidth = width
    }

    fun spawn(position: Offset, velocity: Offset = Offset.Zero): ParticleData {
        val chosenColor = if (particleColors.isNotEmpty()) particleColors.random() else Color.White
        val adjustedFadeFactor = fadeFactor + Random.nextFloat() * 0.5f

        val particle: ParticleData = when {
            activeParticles.size >= maxSize && inactiveParticles.isNotEmpty() -> {
                inactiveParticles.removeAt(inactiveParticles.size - 1).apply {
                    this.position = position
                    this.velocity = velocity
                    this.startY = position.y
                    this.alpha = 1f
                    this.color = chosenColor
                    this.fadeFactor = adjustedFadeFactor
                    this.group = particleGroup
                    this.size = currentParticleSize  // Size setzen
                }
            }
            activeParticles.size < maxSize -> {
                ParticleData(
                    position = position,
                    velocity = velocity,
                    startY = position.y,
                    alpha = 1f,
                    color = chosenColor,
                    fadeFactor = adjustedFadeFactor,
                    group = particleGroup,
                    size = currentParticleSize  // Size setzen
                )
            }
            else -> {
                activeParticles.removeAt(activeParticles.size - 1).apply {
                    this.position = position
                    this.velocity = velocity
                    this.startY = position.y
                    this.alpha = 1f
                    this.color = chosenColor
                    this.fadeFactor = adjustedFadeFactor
                    this.group = particleGroup
                    this.size = currentParticleSize  // Size setzen
                }
            }
        }

        activeParticles.add(particle)
        return particle
    }

    fun update(deltaTime: Float) {
        val iterator = activeParticles.iterator()
        while (iterator.hasNext()) {
            val particle = iterator.next()

            val traveled = particle.startY - particle.position.y
            val rawAlpha = 1f - (traveled / particle.startY) * particle.fadeFactor
            particle.alpha = rawAlpha.coerceIn(0f, 1f)

            // Hier die neue Bedingung
            if (particle.position.y < -20f || particle.position.x < -20f || particle.position.x > screenWidth) {
                iterator.remove()
                inactiveParticles.add(particle)
                continue
            }

            particle.position = Offset(
                x = particle.position.x + particle.velocity.x * deltaTime,
                y = particle.position.y + particle.velocity.y * deltaTime
            )
        }
    }

    fun deactivateParticle(particle: ParticleData) {
        val iterator = activeParticles.iterator()
        while (iterator.hasNext()) {
            if (iterator.next() === particle) {  // Referenzvergleich mit ===
                iterator.remove()
                inactiveParticles.add(particle)
                break
            }
        }
    }

    fun getActiveParticles(): List<ParticleData> = activeParticles
}

class ParticleSystem(
    maxPoolSize: Int = 1000,
    var baseSpeed: Float = 150f,
    private var fadeFactor: Float = 1.0f,
    var particleSize: Float = 10f,
    var emissionRate: Int = 3,
    var angleDeviation: Float = 15f,
    var particleColors: List<Color> = listOf(
        Color.Green, Color.Cyan, Color.Magenta,
        Color.Yellow, Color.Blue, Color.White
    ),
    particleGroup: ParticleGroup = ParticleGroup.PLAYER  // Neue Property
)  {
    private val particlePool = ParticlePool(
        maxSize = maxPoolSize,
        fadeFactor = fadeFactor,
        particleColors = particleColors,
        particleGroup = particleGroup
    ).apply {
        updateParticleSize(particleSize)
    }

    fun updateParameters(
        newBaseSpeed: Float? = null,
        newFadeFactor: Float? = null,
        newParticleSize: Float? = null,
        newEmissionRate: Int? = null,
        newAngleDeviation: Float? = null,
        newParticleColors: List<Color>? = null
    ) {
        newBaseSpeed?.let { baseSpeed = it }
        newFadeFactor?.let { fadeFactor = it }
        newParticleSize?.let { particlePool.updateParticleSize(it) }
        newEmissionRate?.let { emissionRate = it }
        newAngleDeviation?.let { angleDeviation = it }

        if (newParticleColors != null) {
            particleColors = newParticleColors
        }

        particlePool.updateParameters(
            fadeFactor = fadeFactor,
            newParticleColors = particleColors
        )
    }

    fun setScreenDimensions(width: Float) {
        particlePool.setScreenDimensions(width)
    }

    fun getActiveParticles(): List<ParticleData> = particlePool.getActiveParticles()

    fun getParticleCount(): Int = particlePool.particleCount

    fun spawnParticle(position: Offset, velocity: Offset? = null): ParticleData {
        val finalVelocity = velocity ?: run {
            val variationFactor = 0.5f + Random.nextFloat()
            val adjustedBaseSpeed = baseSpeed * variationFactor

            val deviation =
                Random.nextFloat() * angleDeviation * if (Random.nextBoolean()) 1 else -1
            val angleRad = deviation.toRadians()

            Offset(
                x = adjustedBaseSpeed * sin(angleRad),
                y = -adjustedBaseSpeed * cos(angleRad)
            )
        }

        return particlePool.spawn(position, finalVelocity)
    }

    fun updateParticles(deltaTime: Float) {
        particlePool.update(deltaTime)
    }
    fun deactivateParticle(particle: ParticleData) {
        particlePool.deactivateParticle(particle)
    }
}

fun Float.toRadians(): Float = this * (Math.PI.toFloat() / 180f)
