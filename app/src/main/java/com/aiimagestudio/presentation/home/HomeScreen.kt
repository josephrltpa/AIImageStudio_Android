package com.aiimagestudio.presentation.home

import android.graphics.Bitmap
import android.net.Uri
import android.provider.MediaStore
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import com.aiimagestudio.R
import com.aiimagestudio.domain.model.GenerationMode
import java.io.File

/**
 * The app's main (and, for most users, only) screen — deliberately minimal
 * per the product spec: upload -> preview -> prompt -> generate -> save/share.
 * No technical AI settings are shown here; power users reach them via
 * [onOpenSettings].
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onOpenGallery: () -> Unit,
    onOpenModelManager: () -> Unit,
    onOpenSettings: () -> Unit,
    viewModel: HomeViewModel = androidx.hilt.navigation.compose.hiltViewModel()
) {
    val state by viewModel.uiState.collectAsState()
    val context = LocalContext.current

    val imagePicker = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri ?: return@rememberLauncherForActivityResult
        val bitmap = MediaStore.Images.Media.getBitmap(context.contentResolver, uri)
        viewModel.onImageSelected(bitmap)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.app_name), fontWeight = FontWeight.SemiBold) },
                actions = {
                    IconButton(onClick = onOpenGallery) {
                        Icon(Icons.Filled.PhotoLibrary, contentDescription = stringResource(R.string.gallery))
                    }
                    IconButton(onClick = onOpenModelManager) {
                        Icon(Icons.Filled.Storage, contentDescription = stringResource(R.string.model_manager))
                    }
                    IconButton(onClick = onOpenSettings) {
                        Icon(Icons.Filled.Settings, contentDescription = stringResource(R.string.settings))
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
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // --- Upload ---
            OutlinedButton(
                onClick = { imagePicker.launch("image/*") },
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Filled.Upload, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.upload_image))
            }

            ImagePreviewCard(bitmap = state.selectedImage, placeholderLabel = "Image preview")

            // --- Prompt ---
            OutlinedTextField(
                value = state.prompt,
                onValueChange = viewModel::onPromptChanged,
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Prompt") },
                placeholder = { Text(stringResource(R.string.prompt_hint)) },
                minLines = 2,
                maxLines = 4
            )

            // --- Model selector (only two friendly options, no jargon) ---
            SingleChoiceSegment(
                selected = state.mode,
                onSelected = viewModel::onModeChanged
            )

            // --- Generate ---
            Button(
                onClick = viewModel::generate,
                enabled = !state.isGenerating && state.prompt.isNotBlank(),
                modifier = Modifier.fillMaxWidth().height(52.dp)
            ) {
                if (state.isGenerating) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                    Spacer(Modifier.width(10.dp))
                    Text(state.progressLabel ?: "Generating…")
                } else {
                    Text(stringResource(R.string.generate))
                }
            }

            if (state.isGenerating) {
                LinearProgressIndicator(
                    progress = { state.progressFraction },
                    modifier = Modifier.fillMaxWidth()
                )
            }

            // --- Result ---
            if (state.resultBitmap != null) {
                Text("Result", style = MaterialTheme.typography.titleMedium, modifier = Modifier.fillMaxWidth())
                ImagePreviewCard(bitmap = state.resultBitmap, placeholderLabel = "")

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedButton(onClick = { /* already saved automatically on success */ }, modifier = Modifier.weight(1f)) {
                        Icon(Icons.Filled.Save, contentDescription = null)
                        Spacer(Modifier.width(6.dp))
                        Text(stringResource(R.string.save))
                    }
                    Button(
                        onClick = {
                            shareBitmap(context, state.resultBitmap!!)
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Filled.Share, contentDescription = null)
                        Spacer(Modifier.width(6.dp))
                        Text(stringResource(R.string.share))
                    }
                }
            }

            state.errorMessage?.let { error ->
                AssistChip(
                    onClick = viewModel::dismissError,
                    label = { Text(error) },
                    leadingIcon = { Icon(Icons.Filled.ErrorOutline, contentDescription = null) }
                )
            }
        }
    }
}

@Composable
private fun SingleChoiceSegment(
    selected: GenerationMode,
    onSelected: (GenerationMode) -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text("Mode", style = MaterialTheme.typography.labelLarge)
        Spacer(Modifier.height(6.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(
                selected = selected == GenerationMode.INSTRUCT_PIX2PIX_EDIT,
                onClick = { onSelected(GenerationMode.INSTRUCT_PIX2PIX_EDIT) },
                label = { Text("Edit photo") }
            )
            FilterChip(
                selected = selected == GenerationMode.STABLE_DIFFUSION_TXT2IMG,
                onClick = { onSelected(GenerationMode.STABLE_DIFFUSION_TXT2IMG) },
                label = { Text("Create new image") }
            )
        }
    }
}

@Composable
private fun ImagePreviewCard(bitmap: Bitmap?, placeholderLabel: String) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .height(260.dp),
        shape = RoundedCornerShape(16.dp)
    ) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            if (bitmap != null) {
                Image(
                    bitmap = bitmap.asImageBitmap(),
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize()
                )
            } else if (placeholderLabel.isNotEmpty()) {
                Text(placeholderLabel, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

private fun shareBitmap(context: android.content.Context, bitmap: Bitmap) {
    val cacheFile = File(context.cacheDir, "share_${System.currentTimeMillis()}.png")
    cacheFile.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", cacheFile)

    val intent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
        type = "image/png"
        putExtra(android.content.Intent.EXTRA_STREAM, uri)
        addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    context.startActivity(android.content.Intent.createChooser(intent, "Share image"))
}
