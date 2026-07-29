package com.aiimagestudio.presentation.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.aiimagestudio.R
import com.aiimagestudio.domain.model.MemoryMode
import com.aiimagestudio.domain.model.Scheduler

/**
 * The optional Advanced menu called out in the product spec. Everything
 * here has a working default so the main screen never requires a visit.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    viewModel: SettingsViewModel = androidx.hilt.navigation.compose.hiltViewModel()
) {
    val settings by viewModel.settings.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.advanced_settings)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            SettingSection(title = "Resolution: ${settings.width}×${settings.height}") {
                Slider(
                    value = settings.width.toFloat(),
                    valueRange = 384f..768f,
                    steps = 5,
                    onValueChange = { v ->
                        val size = (v / 64).toInt() * 64
                        viewModel.update { it.copy(width = size, height = size) }
                    }
                )
            }

            SettingSection(title = "Steps: ${settings.steps}") {
                Slider(
                    value = settings.steps.toFloat(),
                    valueRange = 10f..50f,
                    onValueChange = { viewModel.update { s -> s.copy(steps = it.toInt()) } }
                )
            }

            SettingSection(title = "CFG scale: %.1f".format(settings.cfgScale)) {
                Slider(
                    value = settings.cfgScale,
                    valueRange = 1f..15f,
                    onValueChange = { viewModel.update { s -> s.copy(cfgScale = it) } }
                )
            }

            SettingSection(title = "Denoising strength: %.2f".format(settings.denoisingStrength)) {
                Slider(
                    value = settings.denoisingStrength,
                    valueRange = 0.1f..1f,
                    onValueChange = { viewModel.update { s -> s.copy(denoisingStrength = it) } }
                )
            }

            SettingSection(title = "Seed") {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(settings.seed?.toString() ?: "Random", modifier = Modifier.weight(1f))
                    TextButton(onClick = { viewModel.update { it.copy(seed = null) } }) {
                        Text("Randomize")
                    }
                }
            }

            SettingSection(title = "Scheduler") {
                SingleChoiceRow(
                    options = Scheduler.values().toList(),
                    selected = settings.scheduler,
                    labelOf = { it.name.replace('_', ' ') },
                    onSelected = { choice -> viewModel.update { it.copy(scheduler = choice) } }
                )
            }

            SettingSection(title = "Memory mode") {
                SingleChoiceRow(
                    options = MemoryMode.values().toList(),
                    selected = settings.memoryMode,
                    labelOf = { it.name.replace('_', ' ') },
                    onSelected = { choice -> viewModel.update { it.copy(memoryMode = choice) } }
                )
                Text(
                    "Auto adapts to your device's available RAM. Low RAM loads model " +
                        "components one at a time to avoid crashes on constrained devices.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun SettingSection(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column {
        Text(title, style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(6.dp))
        content()
    }
}

@Composable
private fun <T> SingleChoiceRow(
    options: List<T>,
    selected: T,
    labelOf: (T) -> String,
    onSelected: (T) -> Unit
) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        options.forEach { option ->
            FilterChip(
                selected = option == selected,
                onClick = { onSelected(option) },
                label = { Text(labelOf(option)) }
            )
        }
    }
}
