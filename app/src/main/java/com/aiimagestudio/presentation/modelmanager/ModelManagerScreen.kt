package com.aiimagestudio.presentation.modelmanager

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.aiimagestudio.R
import com.aiimagestudio.domain.model.AIModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ModelManagerScreen(
    onBack: () -> Unit,
    viewModel: ModelManagerViewModel = androidx.hilt.navigation.compose.hiltViewModel()
) {
    val state by viewModel.uiState.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.model_manager)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        Column(modifier = Modifier.padding(padding).fillMaxSize()) {
            val gb = state.availableStorageBytes / (1024.0 * 1024.0 * 1024.0)
            Text(
                "Available storage: %.1f GB".format(gb),
                modifier = Modifier.padding(16.dp),
                style = MaterialTheme.typography.bodyMedium
            )

            LazyColumn(
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(state.models, key = { it.component }) { model ->
                    ModelRow(
                        model = model,
                        onDownload = { viewModel.download(model.component) },
                        onPause = { viewModel.pause(model.component) },
                        onResume = { viewModel.resume(model.component) },
                        onDelete = { viewModel.delete(model.component) }
                    )
                }
            }
        }
    }
}

@Composable
private fun ModelRow(
    model: AIModel,
    onDownload: () -> Unit,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onDelete: () -> Unit
) {
    Card {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(model.displayName, style = MaterialTheme.typography.titleMedium)
                    Text(
                        model.description,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        "%.0f MB".format(model.totalBytes / (1024.0 * 1024.0)),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                ActionButton(model, onDownload, onPause, onResume, onDelete)
            }

            if (model.isDownloading || model.isPaused) {
                Spacer(Modifier.height(8.dp))
                LinearProgressIndicator(
                    progress = { model.downloadProgress },
                    modifier = Modifier.fillMaxWidth()
                )
                Text(
                    "${(model.downloadProgress * 100).toInt()}%" + if (model.isPaused) " (paused)" else "",
                    style = MaterialTheme.typography.labelLarge
                )
            }

            if (!model.lastError.isNullOrBlank()) {
                Spacer(Modifier.height(8.dp))
                Text(
                    "Download failed: ${model.lastError}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error
                )
            }
        }
    }
}

@Composable
private fun ActionButton(
    model: AIModel,
    onDownload: () -> Unit,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onDelete: () -> Unit
) {
    when {
        model.isInstalled -> IconButton(onClick = onDelete) {
            Icon(Icons.Filled.Delete, contentDescription = "Delete")
        }
        model.isDownloading -> IconButton(onClick = onPause) {
            Icon(Icons.Filled.Pause, contentDescription = "Pause")
        }
        model.isPaused -> IconButton(onClick = onResume) {
            Icon(Icons.Filled.PlayArrow, contentDescription = "Resume")
        }
        else -> IconButton(onClick = onDownload) {
            Icon(Icons.Filled.Download, contentDescription = "Download")
        }
    }
}
