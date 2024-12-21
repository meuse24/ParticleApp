// File: ParticleSystem.kt
package info.meuse24.myapplication

import androidx.compose.runtime.Stable
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

@Stable
data class ParticleData(
    var position: Offset,
    var velocity: Offset,
    var startY: Float,
    var alpha: Float = 1f,
    var color: Color,
    var fadeFactor: Float
)

class ParticlePool(
    private val maxSize: Int = 1000,
    private var fadeFactor: Float = 1.0f,
    var particleColors: List<Color>
) {
    private val activeParticles = ArrayList<ParticleData>(1000)
    private val inactiveParticles = ArrayList<ParticleData>(1000)

    fun updateParameters(fadeFactor: Float? = null, newParticleColors: List<Color>? = null) {
        fadeFactor?.let { this.fadeFactor = it }
        newParticleColors?.let { this.particleColors = it }
    }

    val particleCount: Int
        get() = activeParticles.size

    fun spawn(position: Offset, velocity: Offset): ParticleData {
        val chosenColor = particleColors.random()
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
                }
            }
            activeParticles.size < maxSize -> {
                ParticleData(
                    position = position,
                    velocity = velocity,
                    startY = position.y,
                    alpha = 1f,
                    color = chosenColor,
                    fadeFactor = adjustedFadeFactor
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

            if (particle.position.y < -20f) {
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

    fun getActiveParticles(): List<ParticleData> = activeParticles
}

class ParticleSystem(
    maxPoolSize: Int = 1000,
    private var baseSpeed: Float = 150f,
    private var fadeFactor: Float = 1.0f,
    private var particleSize: Float = 10f,
    internal var emissionRate: Int = 3,
    private var angleDeviation: Float = 15f,
    var particleColors: List<Color> = listOf(
        Color.Green, Color.Cyan, Color.Magenta,
        Color.Yellow, Color.Blue, Color.White
    )
) {
    private val particlePool = ParticlePool(
        maxSize = maxPoolSize,
        fadeFactor = fadeFactor,
        particleColors = particleColors
    )

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
        newParticleSize?.let { particleSize = it }
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

    fun getParticleSize() = particleSize
    fun getEmissionRate() = emissionRate
    fun getActiveParticles(): List<ParticleData> = particlePool.getActiveParticles()
    fun getParticleCount(): Int = particlePool.particleCount

    fun spawnParticle(position: Offset): ParticleData {
        val variationFactor = 0.5f + Random.nextFloat()
        val adjustedBaseSpeed = baseSpeed * variationFactor

        val deviation = Random.nextFloat() * angleDeviation * if (Random.nextBoolean()) 1 else -1
        val angleRad = deviation.toRadians()

        val adjustedVelocity = Offset(
            x = adjustedBaseSpeed * sin(angleRad),
            y = -adjustedBaseSpeed * cos(angleRad)
        )

        return particlePool.spawn(position, adjustedVelocity)
    }

    fun updateParticles(deltaTime: Float) {
        particlePool.update(deltaTime)
    }
}

fun Float.toRadians(): Float = this * (Math.PI.toFloat() / 180f)
