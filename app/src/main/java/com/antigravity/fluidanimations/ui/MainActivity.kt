package com.antigravity.fluidanimations.ui

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.antigravity.fluidanimations.config.AnimationConfig
import com.antigravity.fluidanimations.config.ConfigManager
import com.antigravity.fluidanimations.physics.SpringInterpolator
import com.antigravity.fluidanimations.ui.theme.AppleBlue
import com.antigravity.fluidanimations.ui.theme.AppleGreen
import com.antigravity.fluidanimations.ui.theme.FluidAnimationsTheme
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Загружаем текущие настройки
        ConfigManager.loadFromProvider(this)

        setContent {
            FluidAnimationsTheme {
                MainScreen(
                    isHookActive = isModuleActive(),
                    onConfigChanged = { newConfig ->
                        ConfigManager.saveConfig(this, newConfig)
                    },
                    onRestartTrebuchet = { restartPackage("org.lineageos.trebuchet") },
                    onRestartSystemUi = { restartPackage("com.android.systemui") }
                )
            }
        }
    }

    /**
     * Метод перехватывается в MainHook через LSPosed.
     * Если хук активен, возвращает true.
     */
    fun isModuleActive(): Boolean {
        return false
    }

    private fun restartPackage(pkg: String) {
        Thread {
            try {
                val process = Runtime.getRuntime().exec("su")
                java.io.DataOutputStream(process.outputStream).use { os ->
                    os.writeBytes("am force-stop $pkg\n")
                    os.writeBytes("killall -9 $pkg 2>/dev/null || true\n")
                    os.writeBytes("exit\n")
                    os.flush()
                }
                val exitCode = process.waitFor()
                runOnUiThread {
                    if (exitCode == 0) {
                        Toast.makeText(this, "Перезапущено: $pkg", Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(this, "Ошибка Root (код: $exitCode): проверьте права в Magisk / KernelSU / APatch", Toast.LENGTH_LONG).show()
                    }
                }
            } catch (e: Exception) {
                runOnUiThread {
                    Toast.makeText(this, "Ошибка Root: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }.start()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    isHookActive: Boolean,
    onConfigChanged: (AnimationConfig) -> Unit,
    onRestartTrebuchet: () -> Unit,
    onRestartSystemUi: () -> Unit
) {
    val context = LocalContext.current
    var config by remember { mutableStateOf(ConfigManager.config) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "iOS Fluid Animations",
                            fontWeight = FontWeight.Bold,
                            fontSize = 20.sp
                        )
                        Text(
                            text = "LineageOS 23.2 • Poco X6 Pro 120Hz",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Статус модуля в LSPosed
            item {
                StatusCard(isHookActive = isHookActive)
            }

            // Интерактивный предпросмотр пружины
            item {
                SpringTestCard(config = config)
            }

            // Выбор пресетов
            item {
                PresetsSection(
                    currentConfig = config,
                    onSelectPreset = { presetConfig ->
                        config = presetConfig
                        onConfigChanged(presetConfig)
                    }
                )
            }

            // Тонкие ползунки физики
            item {
                PhysicsSlidersSection(
                    config = config,
                    onConfigUpdated = { updated ->
                        config = updated
                        onConfigChanged(updated)
                    }
                )
            }

            // Переключатели эффектов
            item {
                TogglesSection(
                    config = config,
                    onConfigUpdated = { updated ->
                        config = updated
                        onConfigChanged(updated)
                    }
                )
            }

            // Быстрые действия
            item {
                ActionsSection(
                    onRestartTrebuchet = onRestartTrebuchet,
                    onRestartSystemUi = onRestartSystemUi
                )
            }

            item {
                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }
}

@Composable
fun StatusCard(isHookActive: Boolean) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (isHookActive) AppleGreen.copy(alpha = 0.15f) else MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.4f)
        ),
        shape = RoundedCornerShape(16.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = if (isHookActive) Icons.Default.CheckCircle else Icons.Default.Warning,
                contentDescription = null,
                tint = if (isHookActive) AppleGreen else MaterialTheme.colorScheme.error,
                modifier = Modifier.size(32.dp)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column {
                Text(
                    text = if (isHookActive) "Модуль активен в LSPosed" else "Модуль ожидает активации",
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 16.sp
                )
                Text(
                    text = if (isHookActive) "Хуки внедрены в Trebuchet и SystemUI" else "Включите модуль в приложении LSPosed и перезагрузите телефон",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
fun SpringTestCard(config: AnimationConfig) {
    val coroutineScope = rememberCoroutineScope()
    val animatedProgress = remember { Animatable(0f) }
    var isExpanded by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Тест физики пружины (120 FPS)",
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 15.sp
                )
                Text(
                    text = "Нажмите для теста",
                    style = MaterialTheme.typography.bodySmall,
                    color = AppleBlue
                )
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Интерактивный симулятор окна / иконки
            val currentScale = 0.35f + (animatedProgress.value * 0.65f)
            val currentCorner = (32f - (animatedProgress.value * 14f)).coerceAtLeast(14f).dp

            Box(
                modifier = Modifier
                    .size(160.dp)
                    .scale(currentScale)
                    .clip(RoundedCornerShape(currentCorner))
                    .background(AppleBlue)
                    .clickable {
                        coroutineScope.launch {
                            val opening = !isExpanded
                            val target = if (isExpanded) 0f else 1f
                            isExpanded = !isExpanded

                            // Кастомный расчет пружины для анимации в Compose с учетом направления
                            val duration = if (opening) config.openDurationMs else config.closeDurationMs
                            val interpolator = SpringInterpolator(
                                stiffness = config.stiffness,
                                dampingRatio = config.dampingRatio,
                                durationMs = duration
                            )

                            animatedProgress.animateTo(
                                targetValue = target,
                                animationSpec = tween(
                                    durationMillis = duration.toInt(),
                                    easing = { fraction -> interpolator.getInterpolation(fraction) }
                                )
                            )
                        }
                    },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.PlayArrow,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(36.dp)
                )
            }

            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = if (isExpanded) "Состояние: Открыто (Window • ${config.openDurationMs}мс)" else "Состояние: Иконка (Launcher • ${config.closeDurationMs}мс)",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
fun PresetsSection(
    currentConfig: AnimationConfig,
    onSelectPreset: (AnimationConfig) -> Unit
) {
    Column {
        Text(
            text = "Пресеты анимаций",
            fontWeight = FontWeight.SemiBold,
            fontSize = 16.sp
        )
        Spacer(modifier = Modifier.height(8.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            AnimationConfig.PRESET_MAP.forEach { (name, preset) ->
                val isSelected = currentConfig.presetName == name
                FilterChip(
                    selected = isSelected,
                    onClick = { onSelectPreset(preset) },
                    label = { Text(name, fontSize = 12.sp) }
                )
            }
        }
    }
}

@Composable
fun PhysicsSlidersSection(
    config: AnimationConfig,
    onConfigUpdated: (AnimationConfig) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = "Тонкая настройка физики",
                fontWeight = FontWeight.SemiBold,
                fontSize = 16.sp
            )

            // Жесткость
            SliderItem(
                title = "Жесткость пружины (Stiffness)",
                value = config.stiffness,
                valueText = "${config.stiffness.toInt()}",
                range = 150f..650f,
                onValueChange = {
                    onConfigUpdated(config.copy(presetName = AnimationConfig.PRESET_CUSTOM, stiffness = it))
                }
            )

            // Затухание
            SliderItem(
                title = "Затухание (Damping Ratio)",
                value = config.dampingRatio,
                valueText = String.format("%.2f", config.dampingRatio),
                range = 0.60f..0.98f,
                onValueChange = {
                    onConfigUpdated(config.copy(presetName = AnimationConfig.PRESET_CUSTOM, dampingRatio = it))
                }
            )

            // Длительность открытия (Launch from icon)
            SliderItem(
                title = "Время открытия приложения (мс)",
                value = config.openDurationMs.toFloat(),
                valueText = "${config.openDurationMs} мс",
                range = 180f..550f,
                onValueChange = {
                    onConfigUpdated(config.copy(presetName = AnimationConfig.PRESET_CUSTOM, openDurationMs = it.toLong()))
                }
            )

            // Длительность закрытия (Swipe to Home)
            SliderItem(
                title = "Время закрытия / возврата домой (мс)",
                value = config.closeDurationMs.toFloat(),
                valueText = "${config.closeDurationMs} мс",
                range = 180f..600f,
                onValueChange = {
                    onConfigUpdated(config.copy(presetName = AnimationConfig.PRESET_CUSTOM, closeDurationMs = it.toLong()))
                }
            )

            // Длительность App-to-App
            SliderItem(
                title = "Длительность перехода App-to-App (мс)",
                value = config.appToAppDurationMs.toFloat(),
                valueText = "${config.appToAppDurationMs} мс",
                range = 180f..450f,
                onValueChange = {
                    onConfigUpdated(config.copy(presetName = AnimationConfig.PRESET_CUSTOM, appToAppDurationMs = it.toLong()))
                }
            )

            // Радиус скругления дисплея
            SliderItem(
                title = "Радиус скругления дисплея (Poco X6 Pro: ~38dp)",
                value = config.displayCornerRadiusDp,
                valueText = "${config.displayCornerRadiusDp.toInt()} dp",
                range = 24f..48f,
                onValueChange = {
                    onConfigUpdated(config.copy(presetName = AnimationConfig.PRESET_CUSTOM, displayCornerRadiusDp = it))
                }
            )
        }
    }
}

@Composable
fun SliderItem(
    title: String,
    value: Float,
    valueText: String,
    range: ClosedFloatingPointRange<Float>,
    onValueChange: (Float) -> Unit
) {
    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(text = title, style = MaterialTheme.typography.bodyMedium)
            Text(
                text = valueText,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold,
                color = AppleBlue
            )
        }
        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = range
        )
    }
}

@Composable
fun TogglesSection(
    config: AnimationConfig,
    onConfigUpdated: (AnimationConfig) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text(
                text = "Дополнительные эффекты",
                fontWeight = FontWeight.SemiBold,
                fontSize = 16.sp
            )

            ToggleRow(
                title = "Пружинящий отскок иконки (Landing Bounce)",
                subtitle = "Мягкий отскок при возврате жестом на рабочий стол",
                checked = config.landingBounce,
                onCheckedChange = { onConfigUpdated(config.copy(landingBounce = it)) }
            )

            ToggleRow(
                title = "Адаптивный морфинг скругления углов",
                subtitle = "Плавный переход от радиуса иконки к радиусу экрана",
                checked = config.cornerRadiusMorph,
                onCheckedChange = { onConfigUpdated(config.copy(cornerRadiusMorph = it)) }
            )

            ToggleRow(
                title = "Эффект глубины обоев (Wallpaper Depth)",
                subtitle = "Мягкое отдаление и параллакс фона рабочего стола",
                checked = config.wallpaperDepthZoom,
                onCheckedChange = { onConfigUpdated(config.copy(wallpaperDepthZoom = it)) }
            )
        }
    }
}

@Composable
fun ToggleRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f).padding(end = 16.dp)) {
            Text(text = title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
            Text(text = subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
fun ActionsSection(
    onRestartTrebuchet: () -> Unit,
    onRestartSystemUi: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = "Применение изменений",
            fontWeight = FontWeight.SemiBold,
            fontSize = 16.sp
        )
        Text(
            text = "Настройки применяются на лету, но если лаунчер удерживает старые кэши аниматоров, можно перезапустить его:",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Button(
                onClick = onRestartTrebuchet,
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.buttonColors(containerColor = AppleBlue)
            ) {
                Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Trebuchet", fontSize = 13.sp)
            }
            OutlinedButton(
                onClick = onRestartSystemUi,
                modifier = Modifier.weight(1f)
            ) {
                Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("SystemUI", fontSize = 13.sp)
            }
        }
    }
}
