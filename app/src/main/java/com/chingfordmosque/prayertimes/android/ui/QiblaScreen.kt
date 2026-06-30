package com.chingfordmosque.prayertimes.android.ui

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Explore
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.chingfordmosque.prayertimes.android.qibla.Qibla
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/**
 * Qibla compass screen. Reads device orientation from the sensors and points an indicator at
 * the qibla direction relative to the device's current heading. No location permission is
 * needed — the mosque coordinates are fixed.
 */
@Composable
fun QiblaScreen() {
    val context = LocalContext.current
    val sensorManager = remember {
        context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
    }
    val rotationSensor = remember(sensorManager) {
        sensorManager?.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)
    }
    val accelSensor = remember(sensorManager) {
        sensorManager?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
    }
    val magSensor = remember(sensorManager) {
        sensorManager?.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD)
    }
    val hasSensors = rotationSensor != null || (accelSensor != null && magSensor != null)

    var azimuth by remember { mutableFloatStateOf(0f) }
    var hasReading by remember { androidx.compose.runtime.mutableStateOf(false) }

    DisposableEffect(sensorManager, rotationSensor, accelSensor, magSensor) {
        if (sensorManager == null || !hasSensors) {
            onDispose { }
        } else {
            val rotationMatrix = FloatArray(9)
            val orientation = FloatArray(3)
            var gravity: FloatArray? = null
            var geomagnetic: FloatArray? = null
            var smoothed = 0f
            var initialised = false

            val listener = object : SensorEventListener {
                override fun onSensorChanged(event: SensorEvent) {
                    var haveOrientation = false
                    when (event.sensor.type) {
                        Sensor.TYPE_ROTATION_VECTOR -> {
                            SensorManager.getRotationMatrixFromVector(rotationMatrix, event.values)
                            SensorManager.getOrientation(rotationMatrix, orientation)
                            haveOrientation = true
                        }
                        Sensor.TYPE_ACCELEROMETER -> gravity = event.values.clone()
                        Sensor.TYPE_MAGNETIC_FIELD -> geomagnetic = event.values.clone()
                    }
                    if (!haveOrientation) {
                        val g = gravity
                        val m = geomagnetic
                        if (g != null && m != null &&
                            SensorManager.getRotationMatrix(rotationMatrix, null, g, m)
                        ) {
                            SensorManager.getOrientation(rotationMatrix, orientation)
                            haveOrientation = true
                        }
                    }
                    if (!haveOrientation) return

                    val degrees = ((Math.toDegrees(orientation[0].toDouble())
                        .toFloat()) + 360f) % 360f
                    smoothed = if (!initialised) {
                        initialised = true
                        degrees
                    } else {
                        lowPassAngle(degrees, smoothed)
                    }
                    azimuth = smoothed
                    hasReading = true
                }

                override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
            }

            if (rotationSensor != null) {
                sensorManager.registerListener(
                    listener,
                    rotationSensor,
                    SensorManager.SENSOR_DELAY_UI,
                )
            } else {
                sensorManager.registerListener(
                    listener,
                    accelSensor,
                    SensorManager.SENSOR_DELAY_UI,
                )
                sensorManager.registerListener(
                    listener,
                    magSensor,
                    SensorManager.SENSOR_DELAY_UI,
                )
            }

            onDispose { sensorManager.unregisterListener(listener) }
        }
    }

    val qiblaBearing = remember { Qibla.bearingDegrees.toFloat() }
    // Angle of the qibla relative to the top of the device.
    val relativeToDevice = (((qiblaBearing - azimuth) % 360f) + 360f) % 360f
    val aligned = hasReading && (relativeToDevice <= ALIGN_TOLERANCE || relativeToDevice >= 360f - ALIGN_TOLERANCE)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp, vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        Text(
            text = "Qibla",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.fillMaxWidth(),
        )

        if (!hasSensors) {
            SensorUnavailableCard()
            return@Column
        }

        CompassDial(
            azimuth = azimuth,
            qiblaBearing = qiblaBearing,
            aligned = aligned,
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f),
        )

        Text(
            text = "Qibla ${Math.round(qiblaBearing)}\u00B0",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            color = if (aligned) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.onBackground,
        )

        if (aligned) {
            Text(
                text = "Aligned",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.tertiary,
            )
        }

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
            ),
        ) {
            Text(
                text = "Hold the device flat and away from metal or magnetic objects for an accurate reading.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(16.dp),
            )
        }
    }
}

@Composable
private fun SensorUnavailableCard() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer,
            contentColor = MaterialTheme.colorScheme.onErrorContainer,
        ),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Icon(
                imageVector = Icons.Outlined.Explore,
                contentDescription = null,
            )
            Text(
                text = "Compass sensors are not available on this device, so the qibla " +
                    "direction cannot be shown live. The qibla bearing from the mosque is " +
                    "${Math.round(Qibla.bearingDegrees)}\u00B0 from north.",
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}

@Composable
private fun CompassDial(
    azimuth: Float,
    qiblaBearing: Float,
    aligned: Boolean,
    modifier: Modifier = Modifier,
) {
    val dialColor = MaterialTheme.colorScheme.outline
    val tickColor = MaterialTheme.colorScheme.onSurfaceVariant
    val northColor = MaterialTheme.colorScheme.primary
    val qiblaColor = if (aligned) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.secondary

    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val center = Offset(size.width / 2f, size.height / 2f)
            val radius = min(size.width, size.height) / 2f * 0.85f

            // Outer ring.
            drawCircle(
                color = dialColor.copy(alpha = 0.4f),
                radius = radius,
                center = center,
                style = Stroke(width = 4.dp.toPx()),
            )

            // Rotate the compass face opposite to the device azimuth so North tracks reality.
            rotate(degrees = -azimuth, pivot = center) {
                drawCardinalTicks(center, radius, tickColor, northColor)
            }

            // Qibla indicator relative to device heading.
            rotate(degrees = qiblaBearing - azimuth, pivot = center) {
                drawQiblaPointer(center, radius, qiblaColor)
            }
        }
    }
}

private fun DrawScope.drawCardinalTicks(
    center: Offset,
    radius: Float,
    tickColor: Color,
    northColor: Color,
) {
    val labels = listOf(0f, 90f, 180f, 270f)
    labels.forEach { angle ->
        val rad = Math.toRadians(angle.toDouble() - 90.0)
        val outer = Offset(
            center.x + (radius * cos(rad)).toFloat(),
            center.y + (radius * sin(rad)).toFloat(),
        )
        val inner = Offset(
            center.x + ((radius - 28f) * cos(rad)).toFloat(),
            center.y + ((radius - 28f) * sin(rad)).toFloat(),
        )
        drawLine(
            color = if (angle == 0f) northColor else tickColor,
            start = inner,
            end = outer,
            strokeWidth = if (angle == 0f) 7f else 4f,
        )
    }
    // Emphasise North with a filled marker dot.
    val northRad = Math.toRadians(-90.0)
    drawCircle(
        color = northColor,
        radius = 10f,
        center = Offset(
            center.x + (radius * cos(northRad)).toFloat(),
            center.y + (radius * sin(northRad)).toFloat(),
        ),
    )
}

private fun DrawScope.drawQiblaPointer(
    center: Offset,
    radius: Float,
    color: Color,
) {
    // A triangular arrow pointing toward the top (which, after rotation, is the qibla).
    val tip = Offset(center.x, center.y - radius * 0.78f)
    val baseLeft = Offset(center.x - radius * 0.12f, center.y - radius * 0.45f)
    val baseRight = Offset(center.x + radius * 0.12f, center.y - radius * 0.45f)
    val path = Path().apply {
        moveTo(tip.x, tip.y)
        lineTo(baseLeft.x, baseLeft.y)
        lineTo(baseRight.x, baseRight.y)
        close()
    }
    drawPath(path = path, color = color)
    // Stem from centre to the arrow base.
    drawLine(
        color = color,
        start = center,
        end = Offset(center.x, center.y - radius * 0.45f),
        strokeWidth = 10f,
    )
    drawCircle(color = color, radius = 14f, center = center)
}

/** Low-pass filter that respects circular (wrap-around) angle distance, in degrees. */
private fun lowPassAngle(target: Float, current: Float, factor: Float = 0.15f): Float {
    var diff = target - current
    while (diff > 180f) diff -= 360f
    while (diff < -180f) diff += 360f
    return ((current + factor * diff) % 360f + 360f) % 360f
}

private const val ALIGN_TOLERANCE = 5f
